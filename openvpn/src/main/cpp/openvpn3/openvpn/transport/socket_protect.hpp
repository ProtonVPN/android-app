//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012- OpenVPN Inc.
//
//    SPDX-License-Identifier: MPL-2.0 OR AGPL-3.0-only WITH openvpn3-openssl-exception
//

#ifndef OPENVPN_TRANSPORT_SOCKET_PROTECT_H
#define OPENVPN_TRANSPORT_SOCKET_PROTECT_H

#include <openvpn/addr/ip.hpp>
#ifdef OPENVPN_PLATFORM_UWP
#include <openvpn/transport/uwp_socket_protect.hpp>
#endif

namespace openvpn {
// Used as an interface in cases where the high-level controlling app
// needs early access to newly created transport sockets for making
// property changes.  For example, on Android, we need to "protect"
// the socket from being routed into the VPN tunnel.
class BaseSocketProtect
{
  public:
    virtual ~BaseSocketProtect() = default;

    virtual bool socket_protect(openvpn_io::detail::socket_type socket, IP::Addr endpoint) = 0;
};

#ifdef OPENVPN_PLATFORM_UWP
typedef UWPSocketProtect SocketProtect;
#else
typedef BaseSocketProtect SocketProtect;
#endif
} // namespace openvpn

#endif
