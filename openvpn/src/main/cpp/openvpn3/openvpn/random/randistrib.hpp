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

#include <cstdint>

namespace openvpn {

// Return a uniformly distributed random number in the range [0, end)
// using seed as a random seed.  This version is strictly 32-bit only
// and optimizes by avoiding integer division.
inline std::uint32_t rand32_distribute(const std::uint32_t seed,
                                       const std::uint32_t end)
{
    return (std::uint64_t(seed) * end) >> 32;
}

} // namespace openvpn
