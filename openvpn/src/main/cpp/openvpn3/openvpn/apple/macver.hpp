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

#ifndef OPENVPN_APPLE_MACVER_H
#define OPENVPN_APPLE_MACVER_H

#include <errno.h>
#include <sys/sysctl.h>

#include <string>
#include <sstream>
#include <vector>

#include <openvpn/common/split.hpp>
#include <openvpn/common/number.hpp>
#include <openvpn/apple/ver.hpp>

namespace openvpn::Mac {
class Version : public AppleVersion
{
  public:
    // Mac OS X versions
    // 15.x.x  OS X 10.11.x El Capitan
    // 14.x.x  OS X 10.10.x Yosemite
    // 13.x.x  OS X 10.9.x Mavericks
    // 12.x.x  OS X 10.8.x Mountain Lion
    // 11.x.x  OS X 10.7.x Lion
    // 10.x.x  OS X 10.6.x Snow Leopard
    //  9.x.x  OS X 10.5.x Leopard
    //  8.x.x  OS X 10.4.x Tiger
    //  7.x.x  OS X 10.3.x Panther
    //  6.x.x  OS X 10.2.x Jaguar
    //  5.x    OS X 10.1.x Puma

    enum
    {
        OSX_10_11 = 15,
        OSX_10_10 = 14,
        OSX_10_9 = 13,
        OSX_10_8 = 12,
        OSX_10_7 = 11,
        OSX_10_6 = 10,
    };

    Version()
    {
        char str[256];
        size_t size = sizeof(str);
        int ret = sysctlbyname("kern.osrelease", str, &size, nullptr, 0);
        if (!ret)
            init(std::string(str, size));
    }
};
} // namespace openvpn::Mac

#endif
