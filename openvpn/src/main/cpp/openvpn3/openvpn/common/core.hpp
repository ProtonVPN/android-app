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

// Linux methods for enumerating the number of cores on machine,
// and binding a thread to a particular core.

#pragma once

#include <thread>

#include <openvpn/common/platform.hpp>

#if defined(__APPLE__)
#include <sys/types.h>
#include <sys/sysctl.h>
#elif defined(OPENVPN_PLATFORM_LINUX)
#include <unistd.h>
#elif defined(OPENVPN_PLATFORM_WIN)
#include <windows.h>
#endif

namespace openvpn {

  inline int n_cores()
  {
    int count = std::thread::hardware_concurrency();
    // C++11 allows thread::hardware_concurrency() to return 0, fall back
    // to specific solution if we detect this
    if (count > 0)
      return count;

#if defined(__APPLE__)
    size_t count_len = sizeof(count);
    if (::sysctlbyname("hw.logicalcpu", &count, &count_len, NULL, 0) != 0)
      count = 1;
    return count;
#elif defined(OPENVPN_PLATFORM_LINUX)
    long ret = ::sysconf(_SC_NPROCESSORS_ONLN);
    if (ret <= 0)
      ret = 1;
    return ret;
#elif defined(OPENVPN_PLATFORM_WIN)
    SYSTEM_INFO si;
    ::GetSystemInfo(&si);
    return si.dwNumberOfProcessors;
#else
    return 1;
#endif
  }

}
