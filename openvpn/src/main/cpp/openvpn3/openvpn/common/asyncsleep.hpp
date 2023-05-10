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
