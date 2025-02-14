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

// define an ARCH_x macro that describes our target architecture

#ifndef OPENVPN_COMMON_ARCH_H
#define OPENVPN_COMMON_ARCH_H

#if defined(__amd64__) || defined(__x86_64__) || defined(_M_X64) || defined(_M_AMD64)
#define OPENVPN_ARCH_x86_64
#elif defined(__i386__) || defined(_M_IX86)
#define OPENVPN_ARCH_i386
#elif defined(__aarch64__) || defined(__arm64__) || defined(_M_ARM64)
#define OPENVPN_ARCH_ARM64
#elif defined(__arm__) || defined(_M_ARM)
#define OPENVPN_ARCH_ARM
#endif

#endif
