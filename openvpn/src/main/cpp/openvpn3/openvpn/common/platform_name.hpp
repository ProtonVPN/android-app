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

#ifndef OPENVPN_COMMON_PLATFORM_NAME_H
#define OPENVPN_COMMON_PLATFORM_NAME_H

#include <openvpn/common/size.hpp>
#include <openvpn/common/platform.hpp>

namespace openvpn {

// return a string that describes our platform
inline const char *platform_name()
{
#if defined(OPENVPN_PLATFORM_WIN)
#if defined(OPENVPN_PLATFORM_UWP)
    return "uwp";
#else
    return "win";
#endif // UWP
#elif defined(OPENVPN_PLATFORM_MAC)
    return "mac";
#elif defined(OPENVPN_PLATFORM_IPHONE)
    return "ios";
#elif defined(OPENVPN_PLATFORM_IPHONE_SIMULATOR)
    return "iosim";
#elif defined(OPENVPN_PLATFORM_ANDROID)
    return "android";
#elif defined(OPENVPN_PLATFORM_LINUX)
    return "linux";
#elif defined(OPENVPN_PLATFORM_FREEBSD)
    return "FreeBSD";
#else
    static_assert(false);
#endif
}

} // namespace openvpn

#endif // OPENVPN_COMMON_PLATFORM_NAME_H
