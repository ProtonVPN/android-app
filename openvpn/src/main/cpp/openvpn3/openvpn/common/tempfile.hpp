//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2022 OpenVPN Inc.
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU Affero General Public License Version 3
//    as published by the Free Software Foundation.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU Affero General Public License for more details.
//
//    You should have received a copy of the GNU Affero General Public License
//    along with this program in the COPYING file.
//    If not, see <http://www.gnu.org/licenses/>.

#ifndef OPENVPN_COMMON_TEMPFILE_H
#define OPENVPN_COMMON_TEMPFILE_H

#include <openvpn/common/platform.hpp>

#if defined(OPENVPN_PLATFORM_WIN)
#error temporary file methods not supported on Windows
#endif

#include <stdlib.h>
#include <errno.h>
#include <cstring>     // for memcpy
#include <unistd.h>    // for write, unlink, lseek
#include <sys/types.h> // for lseek

#include <string>
#include <memory>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/numeric_cast.hpp>
#include <openvpn/common/scoped_fd.hpp>
#include <openvpn/common/write.hpp>
#include <openvpn/common/strerror.hpp>
#include <openvpn/buffer/bufread.hpp>

using openvpn::numeric_util::numeric_cast;

namespace openvpn {
class TempFile
{
  public:
    OPENVPN_EXCEPTION(tempfile_exception);

    TempFile(const std::string &fn_template,
             const bool fn_delete)
        : fn(new char[fn_template.length() + 1]),
          del(fn_delete)
    {
        constexpr char pattern[] = "XXXXXX";
        constexpr size_t patternLen = sizeof(pattern) - 1;
        std::memcpy(fn.get(), fn_template.c_str(), fn_template.length() + 1);
        const size_t pos = fn_template.rfind(pattern);
        if (pos != std::string::npos)
        {
            if (fn_template.length() > pos + patternLen)
            {
                const auto suffixlen = fn_template.length() - pos - patternLen;
                fd.reset(::mkstemps(fn.get(), numeric_cast<int>(suffixlen)));
            }
            else
                fd.reset(::mkstemp(fn.get()));
            if (!fd.defined())
            {
                const int eno = errno;
                OPENVPN_THROW(tempfile_exception, "error creating temporary file from template: " << fn_template << " : " << strerror_str(eno));
            }
        }
        else
            OPENVPN_THROW(tempfile_exception, "badly formed temporary file template: " << fn_template);
    }

    ~TempFile()
    {
        fd.close();
        delete_file();
    }

    void reset()
    {
        const off_t off = ::lseek(fd(), 0, SEEK_SET);
        if (off < 0)
        {
            const int eno = errno;
            OPENVPN_THROW(tempfile_exception, "seek error on temporary file: " << filename() << " : " << strerror_str(eno));
        }
        if (off)
            OPENVPN_THROW(tempfile_exception, "unexpected seek on temporary file: " << filename());
    }

    void truncate()
    {
        reset();
        if (::ftruncate(fd(), 0) < 0)
        {
            const int eno = errno;
            OPENVPN_THROW(tempfile_exception, "ftruncate error on temporary file: " << filename() << " : " << strerror_str(eno));
        }
    }

    void write(const std::string &content)
    {
        const ssize_t size = write_retry(fd(), content.c_str(), content.length());
        if (size < 0)
        {
            const int eno = errno;
            OPENVPN_THROW(tempfile_exception, "error writing to temporary file: " << filename() << " : " << strerror_str(eno));
        }
        else if (static_cast<std::string::size_type>(size) != content.length())
        {
            OPENVPN_THROW(tempfile_exception, "incomplete write to temporary file: " << filename());
        }
    }

    std::string read()
    {
        BufferList buflist = buf_read(fd(), filename());
        return buflist.to_string();
    }

    std::string filename() const
    {
        if (fn)
            return fn.get();
        else
            return "";
    }

    void close_file()
    {
        if (!fd.close())
        {
            const int eno = errno;
            OPENVPN_THROW(tempfile_exception, "error closing temporary file: " << filename() << " : " << strerror_str(eno));
        }
    }

    void set_delete(const bool del_flag)
    {
        del = del_flag;
    }

    void delete_file()
    {
        if (fn && del)
        {
            ::unlink(fn.get());
            del = false;
        }
    }

    ScopedFD fd;

  private:
    std::unique_ptr<char[]> fn;
    bool del;
};
} // namespace openvpn

#endif
