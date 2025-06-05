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

#ifndef OPENVPN_COMMON_TO_STRING_H
#define OPENVPN_COMMON_TO_STRING_H

#include <string>
#include <sstream>
#include <type_traits>

#include <openvpn/common/platform.hpp>

/* This file provides an openvpn::to_string function that works for all
   types where either std::to_string is available or the type can be
   streamed to an std::ostringstream. This is useful for types that don't
   have a std::to_string overload. Also useful in cases where std::to_string
   support is not complete.
*/

namespace openvpn {

// Use std::to_string where we can
using std::to_string;

/**
    @brief Convert a value to a string
    @tparam T The type of the value to convert
    @param t The value to convert
    @return std::string Stringified representation of the value
    @note This function uses std::ostringstream to convert the value to a string
    @note This function is enabled only for types that do not have a std::to_string
          overload as long as the type is ostream insertable.
*/
template <typename T>
    requires(!requires(T a) { std::to_string(a); })
            && requires(std::ostream &os, const T &t) { os << t; }
inline std::string to_string(const T &t)
{
    std::ostringstream os;
    os << t;
    return os.str();
}

} // namespace openvpn

#endif
