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

#pragma once
// check if Netlink has been selected at compile time
#ifdef OPENVPN_USE_SITNL
#include <openvpn/tun/linux/client/tunnetlink.hpp>
#define TUN_LINUX openvpn::TunNetlink::TunMethods
#else
#include <openvpn/tun/linux/client/tuniproute.hpp>
#define TUN_LINUX openvpn::TunIPRoute::TunMethods
#endif