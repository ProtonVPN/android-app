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

// Transmit/Receive file descriptors over a unix domain socket

#ifndef OPENVPN_COMMON_XMITFD_H
#define OPENVPN_COMMON_XMITFD_H

#include <string>
#include <memory>
#include <cstring>

#include <sys/types.h>
#include <sys/socket.h>
#include <poll.h>
#include <errno.h>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/strerror.hpp>

namespace openvpn {

class XmitFD
{
  public:
    OPENVPN_EXCEPTION(xmit_fd_error);

    static void xmit_fd(const int sock_fd,
                        const int payload_fd, // optional (set to -1 to disable)
                        const std::string &message,
                        const int timeout_ms)
    {
        unsigned char buf[CMSG_SPACE(sizeof(payload_fd))];
        std::memset(buf, 0, sizeof(buf));

        struct iovec io;
        io.iov_base = const_cast<char *>(message.c_str());
        io.iov_len = message.length();

        struct msghdr msg = {0};
        msg.msg_iov = &io;
        msg.msg_iovlen = 1;

        if (payload_fd >= 0)
        {
            msg.msg_control = buf;
            msg.msg_controllen = sizeof(buf);

            struct cmsghdr *cmsg = CMSG_FIRSTHDR(&msg);
            cmsg->cmsg_level = SOL_SOCKET;
            cmsg->cmsg_type = SCM_RIGHTS;
            cmsg->cmsg_len = CMSG_LEN(sizeof(payload_fd));

            std::memcpy(CMSG_DATA(cmsg), &payload_fd, sizeof(payload_fd));

            msg.msg_controllen = cmsg->cmsg_len;
        }

        poll_wait(sock_fd, true, timeout_ms);
        const ssize_t status = ::sendmsg(sock_fd, &msg, 0);
        if (status < 0)
        {
            const int eno = errno;
            OPENVPN_THROW(xmit_fd_error, "xmit_fd: " << strerror_str(eno));
        }
        else if (status != static_cast<ssize_t>(message.length()))
            OPENVPN_THROW(xmit_fd_error, "xmit_fd: unexpected send size");
    }

    static int recv_fd(const int sock_fd,
                       std::string &message,
                       const size_t buf_size,
                       const int timeout_ms)
    {
        struct msghdr msg = {0};

        std::unique_ptr<char[]> buf(new char[buf_size]);
        struct iovec io = {.iov_base = buf.get(), .iov_len = buf_size};
        msg.msg_iov = &io;
        msg.msg_iovlen = 1;

        unsigned char c_buffer[256];
        msg.msg_control = c_buffer;
        msg.msg_controllen = sizeof(c_buffer);

        poll_wait(sock_fd, false, timeout_ms);
        const ssize_t status = ::recvmsg(sock_fd, &msg, 0);
        if (status < 0)
        {
            const int eno = errno;
            OPENVPN_THROW(xmit_fd_error, "recv_fd: " << strerror_str(eno));
        }
        else if (status == 0)
            OPENVPN_THROW(xmit_fd_error, "recv_fd: eof");
        else if (status > static_cast<ssize_t>(buf_size))
            OPENVPN_THROW(xmit_fd_error, "recv_fd: unexpectedly large message");

        int fd;
        for (struct cmsghdr *cmsg = CMSG_FIRSTHDR(&msg); cmsg != NULL; cmsg = CMSG_NXTHDR(&msg, cmsg))
        {
            if (cmsg->cmsg_len == CMSG_LEN(sizeof(fd))
                && cmsg->cmsg_level == SOL_SOCKET
                && cmsg->cmsg_type == SCM_RIGHTS)
            {
                std::memcpy(&fd, CMSG_DATA(cmsg), sizeof(fd));
                if (fd >= 0)
                {
                    message = std::string(buf.get(), status);
                    return fd;
                }
            }
        }
        OPENVPN_THROW(xmit_fd_error, "recv_fd: no fd in message");
    }

  private:
    static void poll_wait(const int fd,
                          const bool write,
                          const int timeout)
    {
        struct pollfd fds[1];
        fds[0].fd = fd;
        fds[0].events = write ? POLLOUT : (POLLIN | POLLPRI);
        fds[0].revents = 0;
        const int status = ::poll(fds, 1, timeout);
        if (status < 0)
        {
            const int eno = errno;
            OPENVPN_THROW(xmit_fd_error, "poll_wait: poll failed: " << strerror_str(eno));
        }
        else if (status == 0)
            OPENVPN_THROW(xmit_fd_error, "poll_wait: poll timeout");
        else if (status != 1)
            OPENVPN_THROW(xmit_fd_error, "poll_wait: poll failed with unexpected return value=" << status);
    }
};

} // namespace openvpn

#endif
