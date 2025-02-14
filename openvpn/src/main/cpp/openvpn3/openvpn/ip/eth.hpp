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

// Define the Ethernet header

#ifndef OPENVPN_IP_ETH_H
#define OPENVPN_IP_ETH_H

#include <cstdint> // for std::uint32_t, uint16_t, uint8_t

#pragma pack(push)
#pragma pack(1)

namespace openvpn {
struct EthHeader
{
    std::uint8_t dest_mac[6];
    std::uint8_t src_mac[6];
    std::uint16_t ethertype;
};
} // namespace openvpn

#pragma pack(pop)

#endif
