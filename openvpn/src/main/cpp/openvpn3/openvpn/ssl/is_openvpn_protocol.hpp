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
//

#ifndef OPENVPN_SSL_IS_OPENVPN_PROTOCOL_H
#define OPENVPN_SSL_IS_OPENVPN_PROTOCOL_H

#include <algorithm>                // for std::min

#include <openvpn/common/size.hpp>  // for size_t

namespace openvpn {

  // Peek at the first few bytes of a session and
  // distinguishing between OpenVPN or SSL protocols.
  inline bool is_openvpn_protocol(const unsigned char *p, const size_t len)
  {
    const int CONTROL_HARD_RESET_CLIENT_V2 = 7;
    const int CONTROL_HARD_RESET_CLIENT_V3 = 10;
    const int OPCODE_SHIFT = 3;
    const int MIN_INITIAL_PKT_SIZE = 14;

    switch (std::min(len, size_t(3)))
      {
      case 3:
	return p[0] == 0
	  && p[1] >= MIN_INITIAL_PKT_SIZE
	  && (p[2] == (CONTROL_HARD_RESET_CLIENT_V2 << OPCODE_SHIFT)
	      || p[2] == (CONTROL_HARD_RESET_CLIENT_V3 << OPCODE_SHIFT));
      case 2:
	return p[0] == 0 && p[1] >= MIN_INITIAL_PKT_SIZE;
      case 1:
	return p[0] == 0;
      default:
	return true;
      }
  }

}
#endif
