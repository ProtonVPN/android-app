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

// loose emulation of std::clamp for pre-C++17

namespace openvpn {

  template <typename T>
  T clamp(T value, T low, T high)
  {
    if (value < low)
      return low;
    else if (value > high)
      return high;
    else
      return value;
  }

  // like clamp() above, but only clamp non-zero values
  template <typename T>
  T clamp_nonzero(T value, T low, T high)
  {
    if (value)
      return clamp(value, low, high);
    else
      return value;
  }
}
