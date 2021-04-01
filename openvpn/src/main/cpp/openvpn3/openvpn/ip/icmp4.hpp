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

// Define the ICMPv4 header

#pragma once

#include <cstdint> // for std::uint32_t, uint16_t, uint8_t

#include <openvpn/ip/ip4.hpp>

#pragma pack(push)
#pragma pack(1)

namespace openvpn {
  struct ICMPv4 {
    enum {
      ECHO_REQUEST  = 8,
      ECHO_REPLY    = 0,
      DEST_UNREACH  = 3,
      FRAG_NEEDED   = 4,
      MIN_DATA_SIZE = 8
    };

    struct IPv4Header head;

    union {
      struct {
	std::uint8_t type;
	std::uint8_t code;
      };
      std::uint16_t type_code;
    };
    std::uint16_t checksum;

    union {
      struct {
	std::uint16_t id;
	std::uint16_t seq_num;
      };
      struct {
	std::uint16_t unused;
	std::uint16_t nexthop_mtu;
      };
    };
  };
}

#pragma pack(pop)
