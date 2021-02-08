//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2020 OpenVPN Inc.
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

// IPv6 header

#pragma once

#include <cstdint> // for std::uint32_t, uint16_t, uint8_t

#include <openvpn/common/socktypes.hpp>

#pragma pack(push)
#pragma pack(1)

namespace openvpn {

  struct IPv6Header
  {
    std::uint8_t    version_prio;

    std::uint8_t    flow_lbl[3];

    std::uint16_t   payload_len;
    std::uint8_t    nexthdr;
    std::uint8_t    hop_limit;

    struct in6_addr saddr;
    struct in6_addr daddr;
  };
}

#pragma pack(pop)
