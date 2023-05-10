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
#else
    return nullptr;
#endif
}

} // namespace openvpn

#endif // OPENVPN_COMMON_PLATFORM_NAME_H
