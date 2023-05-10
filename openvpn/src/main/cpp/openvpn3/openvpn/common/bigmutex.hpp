//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2022 OpenVPN Inc.
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

// Macro to maintain thread-safety.

// Platforms like UWP and iOS may call core methods
// from another threads. Since core is not thread-safe,
// we provide OPENVPN_ASYNC_HANDLER macro which instantiates
// lock guard. It follows RIAA principle and locks global
// mutex in constructor and unlocks in destructor. This
// guarantees that code in block protected with this macro
// won't be called simultaneously from different threads.

#ifndef OPENVPN_COMMON_BIGMUTEX_H
#define OPENVPN_COMMON_BIGMUTEX_H

#include <mutex>

#include <openvpn/common/extern.hpp>

namespace openvpn {
namespace bigmutex {
OPENVPN_EXTERN std::recursive_mutex the_recursive_mutex;
}

#ifdef OPENVPN_ENABLE_BIGMUTEX
#define OPENVPN_ASYNC_HANDLER \
    std::lock_guard<std::recursive_mutex> lg(bigmutex::the_recursive_mutex);
#else
#define OPENVPN_ASYNC_HANDLER
#endif
} // namespace openvpn

#endif
