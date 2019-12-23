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

// For debugging, reduce effective buffer size for I/O.
// Enable by defining OPENVPN_BUF_CLAMP_READ and/or OPENVPN_BUF_CLAMP_WRITE

#ifndef OPENVPN_BUFFER_BUFCLAMP_H
#define OPENVPN_BUFFER_BUFCLAMP_H

#include <algorithm>

#include <openvpn/common/size.hpp>

namespace openvpn {
  inline size_t buf_clamp_read(const size_t size)
  {
#ifdef OPENVPN_BUF_CLAMP_READ
    return std::min(size, size_t(OPENVPN_BUF_CLAMP_READ));
#else
    return size;
#endif
  }

  inline size_t buf_clamp_write(const size_t size)
  {
#ifdef OPENVPN_BUF_CLAMP_WRITE
    return std::min(size, size_t(OPENVPN_BUF_CLAMP_WRITE));
#else
    return size;
#endif
  }
}

#endif
