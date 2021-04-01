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

// Demangle a C++ name (GCC only)

#ifndef OPENVPN_COMMON_DEMANGLE_H
#define OPENVPN_COMMON_DEMANGLE_H

#include <cxxabi.h>

#include <string>
#include <memory>

namespace openvpn {

  inline std::string cxx_demangle(const char *mangled_name)
  {
    int status;
    std::unique_ptr<char[]> realname;

    realname.reset(abi::__cxa_demangle(mangled_name, 0, 0, &status));
    if (!status)
      return std::string(realname.get());
    else
      return "DEMANGLE_ERROR";
  }

}

#endif
