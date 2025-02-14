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
} // namespace openvpn

#endif
