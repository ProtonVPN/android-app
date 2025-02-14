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

#ifndef OPENVPN_COMMON_STRERROR_H
#define OPENVPN_COMMON_STRERROR_H

#include <string.h>
#include <string>

#include <errno.h>

namespace openvpn {
inline std::string strerror_str(const int errnum)
{
    static const char unknown_err[] = "UNKNOWN_SYSTEM_ERROR";
    char buf[128];

#if defined(__GLIBC__) && (!defined(__USE_XOPEN2K) || defined(__USE_GNU))
    // GNU
    const char *errstr = ::strerror_r(errnum, buf, sizeof(buf));
    if (errstr)
        return std::string(errstr);
#else
    // POSIX
    if (::strerror_r(errnum, buf, sizeof(buf)) == 0)
        return std::string(buf);
#endif
    return std::string(unknown_err);
}
} // namespace openvpn

#endif
