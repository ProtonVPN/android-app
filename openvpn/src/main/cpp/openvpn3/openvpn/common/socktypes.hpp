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

// define stuff like ntohl, ntohs, htonl, htons, etc. in a platform-independent way

#ifndef OPENVPN_COMMON_SOCKTYPES_H
#define OPENVPN_COMMON_SOCKTYPES_H

#include <openvpn/common/platform.hpp>

#ifdef OPENVPN_PLATFORM_WIN
#include <winsock2.h>
#else
#include <arpa/inet.h>
#endif

#endif // OPENVPN_COMMON_SOCKTYPES_H
