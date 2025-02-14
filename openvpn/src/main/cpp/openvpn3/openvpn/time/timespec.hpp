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
#include <cstdint> // for std::uint64_t

namespace openvpn {

typedef std::uint64_t nanotime_t;

namespace TimeSpec {

inline std::uint64_t milliseconds_since_epoch(const struct timespec &ts)
{
    return std::uint64_t(ts.tv_sec) * std::uint64_t(1000)
           + std::uint64_t(ts.tv_nsec) / std::uint64_t(1000000);
}

inline nanotime_t nanoseconds_since_epoch(const struct timespec &ts)
{
    return std::uint64_t(ts.tv_sec) * std::uint64_t(1000000000)
           + std::uint64_t(ts.tv_nsec);
}

} // namespace TimeSpec
} // namespace openvpn
