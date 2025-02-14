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

#ifndef OPENVPN_COMMON_APPVERSION_H
#define OPENVPN_COMMON_APPVERSION_H

// BUILD_VERSION version can be passed on build command line

#include <openvpn/common/stringize.hpp>

#ifdef BUILD_VERSION
#define MY_VERSION OPENVPN_STRINGIZE(BUILD_VERSION)
#else
#define MY_VERSION "0.1.0"
#endif

#endif
