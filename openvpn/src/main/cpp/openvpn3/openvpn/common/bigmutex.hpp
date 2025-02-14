//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012- OpenVPN Inc.
//
//    SPDX-License-Identifier: MPL-2.0 OR AGPL-3.0-only WITH openvpn3-openssl-exception
//

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

namespace openvpn {
namespace bigmutex {
inline std::recursive_mutex the_recursive_mutex;
}

#ifdef OPENVPN_ENABLE_BIGMUTEX
#define OPENVPN_ASYNC_HANDLER \
    std::lock_guard<std::recursive_mutex> lg(bigmutex::the_recursive_mutex);
#else
#define OPENVPN_ASYNC_HANDLER
#endif
} // namespace openvpn

#endif
