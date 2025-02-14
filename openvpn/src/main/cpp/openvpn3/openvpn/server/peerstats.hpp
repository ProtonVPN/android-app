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

#ifndef OPENVPN_SERVER_PEERSTATS_H
#define OPENVPN_SERVER_PEERSTATS_H

#include <string>
#include <cstdint> // for std::uint32_t, uint64_t, etc.

namespace openvpn {

struct PeerStats
{
    std::string to_string() const
    {
        std::string ret;
        ret.reserve(64);
        ret += "[rx=";
        ret += std::to_string(rx_bytes);
        ret += " tx=";
        ret += std::to_string(tx_bytes);
        ret += " status=";
        ret += std::to_string(status);
        ret += ']';
        return ret;
    }

    std::uint64_t rx_bytes = 0;
    std::uint64_t tx_bytes = 0;
    int status = 0;
};

} // namespace openvpn

#endif
