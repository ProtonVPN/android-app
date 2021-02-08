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

#ifndef OPENVPN_COMMON_STRERROR_H
#define OPENVPN_COMMON_STRERROR_H

#include <string.h>
#include <string>

#include <errno.h>

namespace openvpn {
  inline std::string strerror_str(const int errnum)
  {
    static const char unknown_err[] = "UNKNOWN_SYSTEM_ERROR";
    char buf[128];

#if defined(__GLIBC__) && (!defined(__USE_XOPEN2K) || defined(__USE_GNU))
    // GNU
    const char *errstr = ::strerror_r(errnum, buf, sizeof(buf));
    if (errstr)
      return std::string(errstr);
#else
    // POSIX
    if (::strerror_r(errnum, buf, sizeof(buf)) == 0)
      return std::string(buf);
#endif
    return std::string(unknown_err);
  }
}

#endif
