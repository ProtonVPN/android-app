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

// Interruptible sleep

#ifndef OPENVPN_COMMON_ASYNCSLEEP_H
#define OPENVPN_COMMON_ASYNCSLEEP_H

#include <algorithm>

#include <openvpn/common/stop.hpp>
#include <openvpn/common/sleep.hpp>

namespace openvpn {

// returns false if Stop signal prevented full wait
inline bool async_sleep_milliseconds(int milliseconds, Stop *async_stop)
{
    const int milliseconds_per_retry = 250;
    volatile bool stop = false;

    // allow asynchronous stop
    Stop::Scope stop_scope(async_stop, [&stop]()
                           { stop = true; });

    while (milliseconds > 0 && !stop)
    {
        const int ms = std::min(milliseconds, milliseconds_per_retry);
        sleep_milliseconds(ms);
        milliseconds -= ms;
    }

    return !stop;
}

} // namespace openvpn

#endif
