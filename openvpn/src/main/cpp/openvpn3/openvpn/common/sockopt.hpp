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

#ifndef OPENVPN_COMMON_SOCKOPT_H
#define OPENVPN_COMMON_SOCKOPT_H

#include <openvpn/common/platform.hpp>

#if !defined(OPENVPN_PLATFORM_WIN)

#include <unistd.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netinet/tcp.h>

#include <openvpn/common/exception.hpp>

namespace openvpn {
  namespace SockOpt {

#ifdef SO_REUSEPORT
    // set SO_REUSEPORT for inter-thread load balancing
    inline void reuseport(const int fd)
    {
      int on = 1;
      if (::setsockopt(fd, SOL_SOCKET, SO_REUSEPORT,
		     (void *)&on, sizeof(on)) < 0)
	throw Exception("error setting SO_REUSEPORT on socket");
    }
#endif

    // set SO_REUSEADDR for TCP
    inline void reuseaddr(const int fd)
    {
      int on = 1;
      if (::setsockopt(fd, SOL_SOCKET, SO_REUSEADDR,
		     (void *)&on, sizeof(on)) < 0)
	throw Exception("error setting SO_REUSEADDR on socket");
    }

    // set TCP_NODELAY for TCP
    inline void tcp_nodelay(const int fd)
    {
      int state = 1;
      if (::setsockopt(fd, IPPROTO_TCP, TCP_NODELAY,
		     (void *)&state, sizeof(state)) != 0)
	throw Exception("error setting TCP_NODELAY on socket");
    }

    // set FD_CLOEXEC to prevent fd from being passed across execs
    inline void set_cloexec(const int fd)
    {
      if (::fcntl(fd, F_SETFD, FD_CLOEXEC) < 0)
	throw Exception("error setting FD_CLOEXEC on file-descriptor/socket");
    }

    // set non-block mode on socket
    static inline void set_nonblock(const int fd)
    {
      if (::fcntl(fd, F_SETFL, O_NONBLOCK) < 0)
	throw Exception("error setting socket to non-blocking mode");
    }
  }
}

#endif
#endif
