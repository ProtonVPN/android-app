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

#ifndef OPENVPN_COMMON_OLONG_H
#define OPENVPN_COMMON_OLONG_H

// opportunistic long -- 32 bits on 32-bit machines, and 64 bits
// on 64-bit machines.

namespace openvpn {
#if defined(_MSC_VER) && (defined(_M_X64) || defined(_M_ARM64))
typedef long long olong;
typedef unsigned long long oulong;
#else
typedef long olong;
typedef unsigned long oulong;
#endif
} // namespace openvpn

#endif
