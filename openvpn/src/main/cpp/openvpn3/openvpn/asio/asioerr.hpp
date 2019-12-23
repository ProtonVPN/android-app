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

#ifndef OPENVPN_ASIO_ASIOERR_H
#define OPENVPN_ASIO_ASIOERR_H

#include <string>

#include <openvpn/io/io.hpp> // was: #include <asio/error_code.hpp>

namespace openvpn {

  // returns a string describing an i/o error code
  template <typename ErrorCode>
  inline std::string errinfo(ErrorCode err)
  {
    openvpn_io::error_code e(err, openvpn_io::system_category());
    return e.message();
  }

}

#endif
