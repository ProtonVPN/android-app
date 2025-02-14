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

#include <algorithm>

#include <openvpn/time/time.hpp>
#include <openvpn/random/randapi.hpp>

namespace openvpn {

struct TimeSkew
{
    // Skew factors (+/- %).
    // Pass these to skew() via factor parameter.
    enum
    {
        PCT_50 = 0,
        PCT_25 = 1,
        PCT_12_5 = 2,
        PCT_6_25 = 3,
        PCT_3_125 = 4,
        PCT_1_5625 = 5,
    };

    // Skew a duration by some random flux.
    static Time::Duration skew(const Time::Duration &dur, const unsigned int factor, RandomAPI &prng)
    {
        const std::uint32_t bms = static_cast<uint32_t>(std::min(dur.to_binary_ms() >> factor, oulong(0x40000000))); // avoid 32-bit overflow in next step
        const int flux = int(prng.randrange32(bms)) - int(bms / 2);
        return dur + flux;
    }
};

} // namespace openvpn
