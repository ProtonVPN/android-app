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

#pragma once

#include <time.h>
#include <cstdint> // for std::uint64_t

namespace openvpn {

  typedef std::uint64_t nanotime_t;

  namespace TimeSpec {

    inline std::uint64_t milliseconds_since_epoch(const struct timespec& ts)
    {
      return std::uint64_t(ts.tv_sec)  * std::uint64_t(1000)
	   + std::uint64_t(ts.tv_nsec) / std::uint64_t(1000000);
    }

    inline nanotime_t nanoseconds_since_epoch(const struct timespec& ts)
    {
      return std::uint64_t(ts.tv_sec) * std::uint64_t(1000000000)
	   + std::uint64_t(ts.tv_nsec);
    }

  }
}
