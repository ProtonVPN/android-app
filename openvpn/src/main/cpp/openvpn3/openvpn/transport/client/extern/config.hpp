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

#ifndef OPENVPN_TRANSPORT_CLIENT_EXTERN_CONFIG_H
#define OPENVPN_TRANSPORT_CLIENT_EXTERN_CONFIG_H

#include <sstream>
#include <openvpn/transport/client/transbase.hpp>
#include <openvpn/transport/socket_protect.hpp>
#include <openvpn/client/remotelist.hpp>

namespace openvpn
{
  namespace ExternalTransport
  {
    struct Config
    {
      Protocol protocol;
      RemoteList::Ptr remote_list;
      bool server_addr_float = false;
      bool synchronous_dns_lookup = false;
      Frame::Ptr frame;
      SessionStats::Ptr stats;
      SocketProtect* socket_protect = nullptr;
    };
  }
}

#endif
