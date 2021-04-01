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

}

#endif
