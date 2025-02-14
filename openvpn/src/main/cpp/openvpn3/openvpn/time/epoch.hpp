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

#pragma once

#include <time.h>

#include <openvpn/time/timespec.hpp>

namespace openvpn {

typedef std::uint64_t nanotime_t;

inline std::uint64_t milliseconds_since_epoch()
{
    struct timespec ts;
    if (::clock_gettime(CLOCK_REALTIME, &ts))
        return 0;
    return TimeSpec::milliseconds_since_epoch(ts);
}

inline nanotime_t nanoseconds_since_epoch()
{
    struct timespec ts;
    if (::clock_gettime(CLOCK_REALTIME, &ts))
        return 0;
    return TimeSpec::nanoseconds_since_epoch(ts);
}

} // namespace openvpn
