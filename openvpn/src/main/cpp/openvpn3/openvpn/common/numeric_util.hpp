//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2023 OpenVPN Inc.
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

#include <limits>
#include <type_traits>
#include <functional>


namespace openvpn::numeric_util {

// Evaluates true if both template args are integral.
template <typename OutT, typename InT>
constexpr bool is_int_conversion()
{
    return std::is_integral_v<InT> && std::is_integral_v<OutT>;
}

// Returns true if the in param is an unsigned integral type and out param is a signed integral type.
template <typename OutT, typename InT>
constexpr bool is_int_u2s()
{
    return is_int_conversion<OutT, InT>() && std::is_unsigned_v<InT> && std::is_signed_v<OutT>;
}

// Returns true if the in param is a signed integral type and out param is an unsigned integral type.
template <typename OutT, typename InT>
constexpr bool is_int_s2u()
{
    return is_int_conversion<OutT, InT>() && std::is_signed_v<InT> && std::is_unsigned_v<OutT>;
}

// Returns true if both args are integral and the range of OutT can contain the range of InT
template <typename OutT, typename InT>
constexpr bool is_int_rangesafe()
{
    constexpr auto out_digits = std::numeric_limits<OutT>::digits;
    constexpr auto in_digits = std::numeric_limits<InT>::digits;

    return is_int_conversion<OutT, InT>() && !is_int_s2u<OutT, InT>() && out_digits >= in_digits;
}

} // namespace openvpn::numeric_util