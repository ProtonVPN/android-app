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

// loose emulation of std::clamp for pre-C++17

namespace openvpn {

template <typename T>
T clamp(T value, T low, T high)
{
    if (value < low)
        return low;
    else if (value > high)
        return high;
    else
        return value;
}

// like clamp() above, but only clamp non-zero values
template <typename T>
T clamp_nonzero(T value, T low, T high)
{
    if (value)
        return clamp(value, low, high);
    else
        return value;
}
} // namespace openvpn
