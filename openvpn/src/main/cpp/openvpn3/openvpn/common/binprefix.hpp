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

#ifndef OPENVPN_COMMON_BINPREFIX_H
#define OPENVPN_COMMON_BINPREFIX_H

#include <algorithm>        // for std::min, std::max
#include <cstring>          // for std::memset, std::memcpy
#include <cstdint>          // for std::uint32_t, uint64_t

#include <openvpn/common/socktypes.hpp> // for ntohl

namespace openvpn {

  // Return the binary prefix of a big-endian data buffer
  // as a 32 or 64 bit type.

  template <typename T>
  inline T bin_prefix(const unsigned char *data)
  {
    static_assert(sizeof(T) == 4 || sizeof(T) == 8, "size inconsistency");
    if (sizeof(T) == 8)
      return (T(ntohl(*(uint32_t *)&data[0])) << 32) | T(ntohl(*(uint32_t *)&data[4]));
    else // sizeof(T) == 4
      return T(ntohl(*(uint32_t *)&data[0]));
  }

  template <typename T>
  inline T bin_prefix(const unsigned char *data, const size_t len)
  {
    unsigned char d[sizeof(T)]
#ifndef _MSC_VER
      __attribute__((aligned(sizeof(T))))
#endif
      ;
    const size_t l = std::min(len, sizeof(d));
    std::memset(d, 0, sizeof(d));
    std::memcpy(d + sizeof(d) - l, data, l);
    return bin_prefix<T>(d);
  }

  template <typename T>
  inline T bin_prefix_floor(const unsigned char *data, const size_t len, const T floor)
  {
    return std::max(bin_prefix<T>(data, len), floor);
  }

}
#endif
