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

// Common declarations for IPv4 and IPv6

#pragma once

#include <cstdint> // for std::uint32_t, uint16_t, uint8_t

namespace openvpn::IPCommon {

enum
{
    ICMPv4 = 1,  /* ICMPv4 protocol */
    ICMPv6 = 58, /* ICMPv6 protocol */
    IGMP = 2,    /* IGMP protocol */
    TCP = 6,     /* TCP protocol */
    UDP = 17,    /* UDP protocol */
};

enum
{
    IPv4 = 4,
    IPv6 = 6
};

inline unsigned int version(const std::uint8_t version_len_prio)
{
    return (version_len_prio >> 4) & 0x0F;
}

} // namespace openvpn::IPCommon
