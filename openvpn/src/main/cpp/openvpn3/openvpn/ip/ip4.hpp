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

// IPv4 header

#pragma once

#include <cstdint> // for std::uint32_t, uint16_t, uint8_t

#pragma pack(push)
#pragma pack(1)

namespace openvpn {

struct IPv4Header
{
    static unsigned int length(const std::uint8_t version_len)
    {
        return (version_len & 0x0F) << 2;
    }

    static std::uint8_t ver_len(const unsigned int version,
                                const unsigned int len)
    {
        return static_cast<uint8_t>(((len >> 2) & 0x0F) | (version & 0x0F) << 4);
    }

    static bool is_df_set(const unsigned char *data)
    {
        auto *hdr = reinterpret_cast<const IPv4Header *>(data);
        return ntohs(hdr->frag_off) & IPv4Header::DF;
    }

    std::uint8_t version_len;

    std::uint8_t tos;
    std::uint16_t tot_len;
    std::uint16_t id;

    enum
    {
        OFFMASK = 0x1fff,
        DF = 0x4000,
    };
    std::uint16_t frag_off;

    std::uint8_t ttl;

    std::uint8_t protocol;

    std::uint16_t check;
    std::uint32_t saddr;
    std::uint32_t daddr;
    /* The options start here. */
};
} // namespace openvpn

#pragma pack(pop)
