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

// Define the UDP header

#ifndef OPENVPN_IP_UDP_H
#define OPENVPN_IP_UDP_H

#include <openvpn/ip/ipcommon.hpp>

namespace openvpn {

#pragma pack(push)
#pragma pack(1)

struct UDPHeader
{
    std::uint16_t source;
    std::uint16_t dest;
    std::uint16_t len;
    std::uint16_t check;
};

#pragma pack(pop)

inline std::uint16_t udp_checksum(const std::uint8_t *buf,
                                  const unsigned int len_udp,
                                  const std::uint8_t *src_addr,
                                  const std::uint8_t *dest_addr)
{
    std::uint32_t sum = 0;

    /* make 16 bit words out of every two adjacent 8 bit words and  */
    /* calculate the sum of all 16 bit words */
    for (unsigned int i = 0; i < len_udp; i += 2)
    {
        std::uint16_t word16 = static_cast<uint16_t>(((buf[i] << 8) & 0xFF00) + ((i + 1 < len_udp) ? (buf[i + 1] & 0xFF) : 0));
        sum += word16;
    }

    /* add the UDP pseudo header which contains the IP source and destination addresses */
    for (unsigned int i = 0; i < 4; i += 2)
    {
        std::uint16_t word16 = static_cast<uint16_t>(((src_addr[i] << 8) & 0xFF00) + (src_addr[i + 1] & 0xFF));
        sum += word16;
    }
    for (unsigned int i = 0; i < 4; i += 2)
    {
        std::uint16_t word16 = static_cast<uint16_t>(((dest_addr[i] << 8) & 0xFF00) + (dest_addr[i + 1] & 0xFF));
        sum += word16;
    }

    /* the protocol number and the length of the UDP packet */
    sum += (std::uint16_t)IPCommon::UDP + (std::uint16_t)len_udp;

    /* keep only the last 16 bits of the 32 bit calculated sum and add the carries */
    while (sum >> 16)
        sum = (sum & 0xFFFF) + (sum >> 16);

    /* take the one's complement of sum */
    return std::uint16_t(~sum);
}

} // namespace openvpn

#endif
