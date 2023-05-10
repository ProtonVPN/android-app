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

// Unix file read/write

#pragma once

#include <openvpn/common/platform.hpp>

#if defined(OPENVPN_PLATFORM_WIN)
#error unix file methods not supported on Windows
#endif

#include <errno.h>
#include <unistd.h>    // for lseek
#include <sys/types.h> // for lseek, open
#include <sys/stat.h>  // for open
#include <fcntl.h>     // for open
#include <cstdint>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/size.hpp>
#include <openvpn/common/scoped_fd.hpp>
#include <openvpn/common/write.hpp>
#include <openvpn/common/strerror.hpp>
#include <openvpn/common/stat.hpp>
#include <openvpn/common/modstat.hpp>
#include <openvpn/common/stringtempl.hpp>
#include <openvpn/buffer/bufread.hpp>

namespace openvpn {
OPENVPN_EXCEPTION(file_unix_error);

// write binary buffer to file
static constexpr mode_t WRITE_BINARY_UNIX_EXISTING = 010000; // special mode that is useful for writing /proc files
inline void write_binary_unix(const std::string &fn,
                              const mode_t mode,
                              const std::uint64_t mtime_ns, // set explicit modification-time in nanoseconds since epoch, or 0 to defer to system
                              const void *buf,
                              const ssize_t size)
{
    // open
    int flags = O_WRONLY | O_CLOEXEC;
    if (!(mode & WRITE_BINARY_UNIX_EXISTING))
        flags |= O_CREAT | O_TRUNC;
    ScopedFD fd(::open(fn.c_str(), flags, mode & (WRITE_BINARY_UNIX_EXISTING - 1)));
    if (!fd.defined())
    {
        const int eno = errno;
        throw file_unix_error(fn + " : open for write : " + strerror_str(eno));
    }

    // write
    if (size)
    {
        const ssize_t len = write_retry(fd(), buf, size);
        if (len != size)
        {
            if (len == -1)
            {
                const int eno = errno;
                throw file_unix_error(fn + " : write error : " + strerror_str(eno));
            }
            else
                throw file_unix_error(fn + " : incomplete write, request_size=" + std::to_string(size) + " actual_size=" + std::to_string(len));
        }
    }

    // explicit modification time
    if (mtime_ns)
        update_file_mod_time_nanoseconds(fd(), mtime_ns);

    // close
    {
        const int eno = fd.close_with_errno();
        if (eno)
            throw file_unix_error(fn + " : close for write : " + strerror_str(eno));
    }
}

inline void write_binary_unix(const std::string &fn,
                              const mode_t mode,
                              const std::uint64_t mtime_ns,
                              const Buffer &buf)
{
    write_binary_unix(fn, mode, mtime_ns, buf.c_data(), buf.size());
}

inline void write_binary_unix(const std::string &fn,
                              const mode_t mode,
                              const std::uint64_t mtime_ns,
                              const ConstBuffer &buf)
{
    write_binary_unix(fn, mode, mtime_ns, buf.c_data(), buf.size());
}

inline void write_text_unix(const std::string &fn,
                            const mode_t mode,
                            const std::uint64_t mtime_ns,
                            const std::string &content)
{
    write_binary_unix(fn, mode, mtime_ns, content.c_str(), content.length());
}

enum
{ // MUST be distinct from BufferAllocated flags
    NULL_ON_ENOENT = (1 << 8),
};
inline BufferPtr read_binary_unix(const std::string &fn,
                                  const std::uint64_t max_size = 0,
                                  const unsigned int buffer_flags = 0,
                                  std::uint64_t *mtime_ns = nullptr)
{
    // open
    ScopedFD fd(::open(fn.c_str(), O_RDONLY | O_CLOEXEC));
    if (!fd.defined())
    {
        const int eno = errno;
        if ((buffer_flags & NULL_ON_ENOENT) && eno == ENOENT)
            return BufferPtr();
        throw file_unix_error(fn + " : open for read : " + strerror_str(eno));
    }

    // get file timestamp
    if (mtime_ns)
        *mtime_ns = fd_mod_time_nanoseconds(fd());

    // get file length
    const off_t length = ::lseek(fd(), 0, SEEK_END);
    if (length < 0)
    {
        const int eno = errno;
        throw file_unix_error(fn + " : seek end error : " + strerror_str(eno));
    }
    if (::lseek(fd(), 0, SEEK_SET) != 0)
    {
        const int eno = errno;
        throw file_unix_error(fn + " : seek begin error : " + strerror_str(eno));
    }

    // maximum size exceeded?
    if (max_size && std::uint64_t(length) > max_size)
        throw file_unix_error(fn + " : file too large [" + std::to_string(length) + '/' + std::to_string(max_size) + ']');

    // allocate buffer
    BufferPtr bp = new BufferAllocated(size_t(length), buffer_flags);

    // read file content into buffer
    while (buf_read(fd(), *bp, fn))
        ;

    // check for close error
    {
        const int eno = fd.close_with_errno();
        if (eno)
            throw file_unix_error(fn + " : close for read : " + strerror_str(eno));
    }

    return bp;
}

// read file into a fixed buffer, return zero or errno
template <typename STRING>
inline int read_binary_unix_fast(const STRING &fn,
                                 Buffer &out,
                                 std::uint64_t *mtime_ns = nullptr)
{
    ScopedFD fd(::open(StringTempl::to_cstring(fn), O_RDONLY | O_CLOEXEC));
    if (!fd.defined())
        return errno;
    if (mtime_ns)
        *mtime_ns = fd_mod_time_nanoseconds(fd());
    while (true)
    {
        const size_t remaining = out.remaining(0);
        if (!remaining)
            return EAGAIN; // note that we also return EAGAIN if buffer is exactly the same size as content
        const ssize_t status = ::read(fd(), out.data_end(), remaining);
        if (status == 0)
            return 0;
        else if (status < 0)
            return errno;
        out.inc_size(status);
    }
}

inline std::string read_text_unix(const std::string &filename,
                                  const std::uint64_t max_size = 0,
                                  const unsigned int buffer_flags = 0,
                                  std::uint64_t *mtime_ns = nullptr)
{
    BufferPtr bp = read_binary_unix(filename, max_size, buffer_flags, mtime_ns);
    if (bp)
        return buf_to_string(*bp);
    else
        return std::string();
}
} // namespace openvpn
