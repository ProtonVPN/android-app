//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2022 OpenVPN Inc.
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU Affero General Public License Version 3
//    as published by the Free Software Foundation.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU Affero General Public License for more details.
//
//    You should have received a copy of the GNU Affero General Public License
//    along with this program in the COPYING file.
//    If not, see <http://www.gnu.org/licenses/>.

// Static regexes for validation of IP addresses

#pragma once

#include <regex>

#include <openvpn/common/extern.hpp>
#include <openvpn/addr/regex.hpp>

namespace openvpn {
namespace IP {
OPENVPN_EXTERN const std::regex re_v4(v4_regex(), std::regex_constants::ECMAScript | std::regex_constants::nosubs);
OPENVPN_EXTERN const std::regex re_v6(v6_regex(), std::regex_constants::ECMAScript | std::regex_constants::nosubs);

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
} // namespace IP
} // namespace openvpn
