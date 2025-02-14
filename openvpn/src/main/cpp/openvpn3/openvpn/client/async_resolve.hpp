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

#ifndef OPENVPN_CLIENT_ASYNC_RESOLVE_H
#define OPENVPN_CLIENT_ASYNC_RESOLVE_H

#ifdef USE_ASIO
#include <openvpn/client/async_resolve/asio.hpp>
#else
#include <openvpn/client/async_resolve/generic.hpp>
#endif

// create shortcuts for common templated classes
namespace openvpn {
typedef AsyncResolvable<openvpn_io::ip::udp::resolver> AsyncResolvableUDP;
typedef AsyncResolvable<openvpn_io::ip::tcp::resolver> AsyncResolvableTCP;
} // namespace openvpn

#endif /* OPENVPN_CLIENT_ASYNC_RESOLVE_H */
