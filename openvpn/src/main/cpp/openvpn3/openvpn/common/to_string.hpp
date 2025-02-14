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

// Define openvpn::to_string() to work around the fact that
// std::to_string() is missing on Android.
// http://stackoverflow.com/questions/22774009/android-ndk-stdto-string-support

#ifndef OPENVPN_COMMON_TO_STRING_H
#define OPENVPN_COMMON_TO_STRING_H

#include <string>
#include <sstream>
#include <type_traits>

#include <openvpn/common/platform.hpp>

namespace openvpn {

// Convert an arbitrary argument to a string.

#ifndef OPENVPN_PLATFORM_ANDROID
// numeric types
template <typename T,
          typename std::enable_if<std::is_arithmetic<T>::value, int>::type = 0>
inline std::string to_string(T value)
{
    return std::to_string(value);
}
#endif

// non-numeric types
template <typename T
#ifndef OPENVPN_PLATFORM_ANDROID
          ,
          typename std::enable_if<!std::is_arithmetic<T>::value, int>::type = 0
#endif
          >
inline std::string to_string(const T &value)
{
    std::ostringstream os;
    os << value;
    return os.str();
}
} // namespace openvpn

#endif
