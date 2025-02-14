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

// IPv6 header

#pragma once

#include <cstdint> // for std::uint32_t, uint16_t, uint8_t

#include <openvpn/common/socktypes.hpp>

#pragma pack(push)
#pragma pack(1)

namespace openvpn {

struct IPv6Header
{
    std::uint8_t version_prio;

    std::uint8_t flow_lbl[3];

    std::uint16_t payload_len;
    std::uint8_t nexthdr;
    std::uint8_t hop_limit;

    struct in6_addr saddr;
    struct in6_addr daddr;
};
} // namespace openvpn

#pragma pack(pop)
