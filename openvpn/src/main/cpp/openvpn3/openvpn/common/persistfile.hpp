//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2017 OpenVPN Inc.
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

#ifndef OPENVPN_COMMON_PERSISTFILE_H
#define OPENVPN_COMMON_PERSISTFILE_H

#include <sys/types.h> // for open(), lseek(), ftruncate()
#include <sys/stat.h>  // for open()
#include <fcntl.h>     // for open()
#include <unistd.h>    // for write(), lseek(), ftruncate()
#include <errno.h>

#include <string>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/scoped_fd.hpp>
#include <openvpn/common/write.hpp>
#include <openvpn/common/strerror.hpp>
#include <openvpn/buffer/buffer.hpp>

namespace openvpn {
  class PersistentFile
  {
  public:
    PersistentFile(const std::string& fn_arg)
      : fn(fn_arg)
    {
      const int f = ::open(fn.c_str(), O_WRONLY|O_CREAT|O_CLOEXEC, S_IRUSR|S_IWUSR);
      if (f < 0)
	syserr("open");
      fd.reset(f);
    }

    void write(const void *buf, size_t count)
    {
      const off_t off = ::lseek(fd(), 0, SEEK_SET);
      if (off < 0)
	syserr("seek");
      if (off)
	err("unexpected seek");
      if (::ftruncate(fd(), 0) < 0)
	syserr("truncate");
      const ssize_t len = write_retry(fd(), buf, count);
      if (len < 0)
	syserr("write");
      if (len != count || len != ::lseek(fd(), 0, SEEK_CUR))
	err("incomplete write");
      if (::ftruncate(fd(), len) < 0)
	syserr("truncate");
    }

    struct stat stat()
    {
      struct stat s;
      if (::fstat(fd(), &s) < 0)
	syserr("fstat");
      return s;
    }

    void write(const Buffer& buf)
    {
      write(buf.c_data(), buf.size());
    }

    void write(const std::string& str)
    {
      write(str.c_str(), str.length());
    }

  private:
    void syserr(const char *type)
    {
      const int eno = errno;
      OPENVPN_THROW_EXCEPTION(fn << " : " << type << " error : " << strerror_str(eno));
    }

    void err(const char *type)
    {
      OPENVPN_THROW_EXCEPTION(fn << " : " << type << " error");
    }

    std::string fn;
    ScopedFD fd;
  };
}

#endif
