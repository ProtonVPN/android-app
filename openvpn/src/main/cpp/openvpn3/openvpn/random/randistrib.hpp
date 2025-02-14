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

#include <cstdint>

namespace openvpn {

// Return a uniformly distributed random number in the range [0, end)
// using seed as a random seed.  This version is strictly 32-bit only
// and optimizes by avoiding integer division.
inline std::uint32_t rand32_distribute(const std::uint32_t seed,
                                       const std::uint32_t end)
{
    return static_cast<uint32_t>((std::uint64_t(seed) * end) >> 32);
}

} // namespace openvpn
