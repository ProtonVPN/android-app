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

#ifndef OPENVPN_COMMON_PIPE_H
#define OPENVPN_COMMON_PIPE_H

#include <unistd.h>
#include <errno.h>

#include <string>

#include <openvpn/io/io.hpp>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/scoped_fd.hpp>
#include <openvpn/common/strerror.hpp>
#include <openvpn/buffer/buflist.hpp>

namespace openvpn {
namespace Pipe {
class SD
{
  public:
    SD(openvpn_io::io_context &io_context, ScopedFD &fd)
    {
        if (fd.defined())
            sd.reset(new openvpn_io::posix::stream_descriptor(io_context, fd.release()));
    }

    bool defined() const
    {
        return bool(sd);
    }

  protected:
    std::unique_ptr<openvpn_io::posix::stream_descriptor> sd;
};

class SD_OUT : public SD
{
  public:
    SD_OUT(openvpn_io::io_context &io_context, const std::string &content, ScopedFD &fd)
        : SD(io_context, fd)
    {
        if (defined())
        {
            buf = buf_alloc_from_string(content);
            queue_write();
        }
    }

  private:
    void queue_write()
    {
        sd->async_write_some(buf.const_buffer_limit(2048),
                             [this](const openvpn_io::error_code &ec, const size_t bytes_sent)
                             {
            if (!ec && bytes_sent < buf.size())
            {
                buf.advance(bytes_sent);
                queue_write();
            }
            else
            {
                sd->close();
            }
        });
    }

    BufferAllocated buf;
};

class SD_IN : public SD
{
  public:
    SD_IN(openvpn_io::io_context &io_context, ScopedFD &fd)
        : SD(io_context, fd)
    {
        if (defined())
            queue_read();
    }

    const std::string content() const
    {
        return data.to_string();
    }

  private:
    void queue_read()
    {
        buf.reset(0, 2048, 0);
        sd->async_read_some(buf.mutable_buffer_clamp(),
                            [this](const openvpn_io::error_code &ec, const size_t bytes_recvd)
                            {
            if (!ec)
            {
                buf.set_size(bytes_recvd);
                data.put_consume(buf);
                queue_read();
            }
            else
            {
                sd->close();
            }
        });
    }

    BufferAllocated buf;
    BufferList data;
};

inline void make_pipe(int fd[2])
{
    if (::pipe(fd) < 0)
    {
        const int eno = errno;
        OPENVPN_THROW_EXCEPTION("error creating pipe : " << strerror_str(eno));
    }
}

inline void make_pipe(ScopedFD &read, ScopedFD &write)
{
    int fd[2];
    make_pipe(fd);
    read.reset(fd[0]);
    write.reset(fd[1]);
}
} // namespace Pipe
} // namespace openvpn

#endif
