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

// Define the ICMPv6 header

#pragma once

#include <cstdint> // for std::uint32_t, uint16_t, uint8_t

#include <openvpn/ip/ip6.hpp>

#pragma pack(push)
#pragma pack(1)

namespace openvpn {

struct ICMPv6
{
    enum
    {
        ECHO_REQUEST = 128,
        ECHO_REPLY = 129,
        DEST_UNREACH = 1,
        PACKET_TOO_BIG = 2
    };

    struct IPv6Header head;

    union {
        struct
        {
            std::uint8_t type;
            std::uint8_t code;
        };
        std::uint16_t type_code;
    };
    std::uint16_t checksum;

    union {
        struct
        {
            std::uint16_t id;
            std::uint16_t seq_num;
        };
        std::uint32_t mtu;
    };
};
} // namespace openvpn

#pragma pack(pop)
