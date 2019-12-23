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

// Portable millisecond sleep

#ifndef OPENVPN_COMMON_SLEEP_H
#define OPENVPN_COMMON_SLEEP_H

#include <openvpn/common/platform.hpp>

#ifdef OPENVPN_PLATFORM_WIN
#include <windows.h>
#else
#include <time.h>
#endif

namespace openvpn {
  inline bool sleep_milliseconds(const unsigned int milliseconds)
  {
#ifdef OPENVPN_PLATFORM_WIN
    ::Sleep(milliseconds);
    return true;
#else
    struct timespec ts;
    ts.tv_sec = milliseconds / 1000U;
    ts.tv_nsec = (milliseconds % 1000U) * 1000000U;
    return ::nanosleep(&ts, nullptr) == 0;
#endif
  }
}

#endif
