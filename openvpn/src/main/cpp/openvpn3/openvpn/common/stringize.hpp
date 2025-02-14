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

#ifndef OPENVPN_COMMON_STRINGIZE_H
#define OPENVPN_COMMON_STRINGIZE_H

// OPENVPN_STRINGIZE(x) -- put double-quotes around x

#define OPENVPN_STRINGIZE(x) OPENVPN_STRINGIZE2(x)
#define OPENVPN_STRINGIZE2(x) #x

#endif
