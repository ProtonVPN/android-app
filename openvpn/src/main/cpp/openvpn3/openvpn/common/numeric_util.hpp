//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2023- OpenVPN Inc.
//
//    SPDX-License-Identifier: MPL-2.0 OR AGPL-3.0-only WITH openvpn3-openssl-exception
//



#pragma once

#include <cstdint>
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

/* ============================================================================================================= */
//  is_safe_conversion
/* ============================================================================================================= */

/**
 * @brief Returns true if the given value can be contained by the out type
 *
 * @tparam OutT Output type
 * @tparam InT  Input type
 * @param inVal Input value
 * @return true | false
 */

template <typename OutT, typename InT>
bool is_safe_conversion(InT inVal)
{
    if constexpr (!numeric_util::is_int_rangesafe<OutT, InT>())
    {
        if constexpr (numeric_util::is_int_u2s<OutT, InT>())
        {
            auto unsignedInVal = static_cast<uintmax_t>(inVal);
            auto outMax = static_cast<uintmax_t>(std::numeric_limits<OutT>::max());
            if (outMax < unsignedInVal)
                return false;
        }
        else if constexpr (numeric_util::is_int_s2u<OutT, InT>())
        {
            auto lowerVal = static_cast<uintmax_t>(std::max(inVal, InT(0)));
            auto upperLimit = static_cast<uintmax_t>(std::numeric_limits<OutT>::max());
            if (inVal < 0 || lowerVal > upperLimit)
                return false;
        }
        else
        {
            auto outMin = static_cast<InT>(std::numeric_limits<OutT>::min());
            auto outMax = static_cast<InT>(std::numeric_limits<OutT>::max());
            if (inVal < outMin || inVal > outMax)
                return false;
        }
    }

    return true;
}

} // namespace openvpn::numeric_util