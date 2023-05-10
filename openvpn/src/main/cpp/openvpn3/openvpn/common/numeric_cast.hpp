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

#include "numeric_util.hpp"

#include <stdexcept>

#include <openvpn/common/exception.hpp> // For OPENVPN_EXCEPTION_INHERIT

/***
 * @brief Exception type for numeric conversion failures
 */
OPENVPN_EXCEPTION_INHERIT(std::range_error, numeric_out_of_range);

namespace openvpn::numeric_util {

/* ============================================================================================================= */
//  numeric_cast
/* ============================================================================================================= */

/**
 * @brief Tests attempted casts to ensure the input value does not exceed the capacity of the output type
 *
 *  If the types are the same, or the range of the output type equals or exceeds the range of the input type
 *  we just cast and return the value which should ideally optimize away completely. Otherwise we do appropriate
 *  range checks and if those succeed we cast, otherwise the failure exception openvpn::numeric_out_of_range
 *  is thrown.
 *
 *  Example:
 *
 *      int64_t s64 = std::numeric_limits<int64_t>::max();
 *      EXPECT_THROW(numeric_cast<int16_t>(s64), numeric_out_of_range);
 *
 *  @param inVal The value to be converted.
 *  @return The safely converted inVal.
 *  @tparam InT  Source (input) type, inferred from 'inVal'
 *  @tparam OutT Desired result type
 */
template <typename OutT, typename InT>
OutT numeric_cast(InT inVal)
{
    if constexpr (!numeric_util::is_int_rangesafe<OutT, InT>() && numeric_util::is_int_u2s<OutT, InT>())
    {
        // Conversion to uintmax_t should be safe for ::max() in all integral cases
        if (static_cast<uintmax_t>(inVal) > static_cast<uintmax_t>(std::numeric_limits<OutT>::max()))
        {
            throw numeric_out_of_range("Range exceeded for unsigned --> signed integer conversion");
        }
    }
    else if constexpr (!numeric_util::is_int_rangesafe<OutT, InT>() && numeric_util::is_int_s2u<OutT, InT>())
    {
        // Cast to uintmax_t only applied if inVal is positive ...
        if (inVal < 0 || static_cast<uintmax_t>(inVal) > static_cast<uintmax_t>(std::numeric_limits<OutT>::max()))
        {
            throw numeric_out_of_range("Range exceeded for signed --> unsigned integer conversion");
        }
    }
    else if constexpr (!numeric_util::is_int_rangesafe<OutT, InT>())
    {
        // We already know the in and out are sign compatible
        if (std::numeric_limits<OutT>::min() > inVal || std::numeric_limits<OutT>::max() < inVal)
        {
            throw numeric_out_of_range("Range exceeded for integer conversion");
        }
    }

    return static_cast<OutT>(inVal);
}

} // namespace openvpn::numeric_util