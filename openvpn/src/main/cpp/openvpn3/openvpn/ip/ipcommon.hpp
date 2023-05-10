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

// Common declarations for IPv4 and IPv6

#pragma once

#include <cstdint> // for std::uint32_t, uint16_t, uint8_t

namespace openvpn {
namespace IPCommon {

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

} // namespace IPCommon
} // namespace openvpn
