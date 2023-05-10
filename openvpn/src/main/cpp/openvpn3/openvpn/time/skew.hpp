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
        const std::uint32_t bms = std::min(dur.to_binary_ms() >> factor, oulong(0x40000000)); // avoid 32-bit overflow in next step
        const int flux = int(prng.randrange32(bms)) - int(bms / 2);
        return dur + flux;
    }
};

} // namespace openvpn
