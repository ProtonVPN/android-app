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

#ifndef OPENVPN_COMMON_LOGROTATE_H
#define OPENVPN_COMMON_LOGROTATE_H

#include <stdio.h> // for rename()

#include <openvpn/common/to_string.hpp>

namespace openvpn {
inline void log_rotate(const std::string &fn, const int max_versions)
{
    for (int i = max_versions - 1; i >= 0; --i)
    {
        std::string src;
        if (i)
            src = fn + '.' + to_string(i);
        else
            src = fn;
        std::string dest = fn + '.' + to_string(i + 1);
        ::rename(src.c_str(), dest.c_str());
    }
}
} // namespace openvpn

#endif
