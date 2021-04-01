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

#ifndef OPENVPN_COMMON_OLONG_H
#define OPENVPN_COMMON_OLONG_H

// opportunistic long -- 32 bits on 32-bit machines, and 64 bits
// on 64-bit machines.

namespace openvpn {
#if defined(_MSC_VER) && defined(_M_X64)
  typedef long long olong;
  typedef unsigned long long oulong;
#else
  typedef long olong;
  typedef unsigned long oulong;
#endif
}

#endif
