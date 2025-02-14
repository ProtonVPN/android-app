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

#ifndef OPENVPN_COMMON_GETPW_H
#define OPENVPN_COMMON_GETPW_H

#include <openvpn/common/platform.hpp>

#if !defined(OPENVPN_PLATFORM_WIN)
#include <pwd.h>
#include <unistd.h>
#else
#include <openvpn/win/console.hpp>
#endif

#include <string>

#include <openvpn/common/exception.hpp>

namespace openvpn {
inline std::string get_password(const char *prompt)
{
#if !defined(OPENVPN_PLATFORM_WIN)
    char *ret = getpass(prompt);
    return ret;
#else
    Win::Console::Input i{true};
    return i.get_password(prompt);
#endif
}
} // namespace openvpn

#endif
