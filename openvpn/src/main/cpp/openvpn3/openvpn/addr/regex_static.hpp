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

// Static regexes for validation of IP addresses

#pragma once

#include <regex>

#include <openvpn/addr/regex.hpp>

namespace openvpn::IP {
inline const std::regex re_v4(v4_regex(), std::regex_constants::ECMAScript | std::regex_constants::nosubs);
inline const std::regex re_v6(v6_regex(), std::regex_constants::ECMAScript | std::regex_constants::nosubs);

inline bool is_ipv4_address(const std::string &host)
{
    return std::regex_match(host, IP::re_v4);
}

inline bool is_ipv6_address(const std::string &host)
{
    return std::regex_match(host, IP::re_v6);
}

inline bool is_ip_address(const std::string &host)
{
    return is_ipv4_address(host) || is_ipv6_address(host);
}
} // namespace openvpn::IP
