//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2017 OpenVPN Inc.
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
      return ((len >> 2) & 0x0F) | (version & 0x0F) << 4;
    }

    std::uint8_t    version_len;

    std::uint8_t    tos;
    std::uint16_t   tot_len;
    std::uint16_t   id;

    enum {
      OFFMASK=0x1fff,
    };
    std::uint16_t   frag_off;

    std::uint8_t    ttl;

    std::uint8_t    protocol;

    std::uint16_t   check;
    std::uint32_t   saddr;
    std::uint32_t   daddr;
    /* The options start here. */
  };
}

#pragma pack(pop)
