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

#include <string>
#include <type_traits>

#include <openvpn/common/stringtempl.hpp>

namespace openvpn::StringTempl {

// empty

// for objects that define an empty method
template <typename T>
inline auto empty(const T &t) -> decltype(t.empty())
{
    return t.empty();
}

// for numerical values
template <typename T,
          typename std::enable_if<std::is_arithmetic<T>::value, int>::type = 0>
inline bool empty(T value)
{
    return false;
}

// to_string

// for objects that define a to_string() method
template <typename T>
inline auto to_string(const T &t) -> decltype(t.to_string())
{
    return t.to_string();
}

// for numerical values
template <typename T,
          typename std::enable_if<std::is_arithmetic<T>::value, int>::type = 0>
inline std::string to_string(T value)
{
    return std::to_string(value);
}

} // namespace openvpn::StringTempl
