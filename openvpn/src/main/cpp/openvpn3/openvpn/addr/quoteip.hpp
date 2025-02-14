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

#include <string>

namespace openvpn {

// return ip_addr in brackets if it is IPv6
std::string quote_ip(const std::string &ip_addr)
{
    if (ip_addr.find(':') != std::string::npos)
        return '[' + ip_addr + ']';
    else
        return ip_addr;
}
} // namespace openvpn
