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

// define a TARGET_x macro that describes our build target

#ifndef OPENVPN_COMMON_PLATFORM_H
#define OPENVPN_COMMON_PLATFORM_H

#if defined(_WIN32)
#define OPENVPN_PLATFORM_WIN
#if defined(__cplusplus_winrt)
#include <winapifamily.h>
#if WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_APP) && !WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_DESKTOP)
#define OPENVPN_PLATFORM_UWP
#endif // WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_APP)
#endif // defined(__cplusplus_winrt)
#elif defined(__FreeBSD__)
#define OPENVPN_PLATFORM_FREEBSD
#elif defined(__APPLE__)
#include "TargetConditionals.h"
#if TARGET_OS_IPHONE // includes iPad
#define OPENVPN_PLATFORM_IPHONE
#define OPENVPN_PLATFORM_IPHONE_DEVICE
#elif TARGET_IPHONE_SIMULATOR // includes iPad
#define OPENVPN_PLATFORM_IPHONE
#define OPENVPN_PLATFORM_IPHONE_SIMULATOR
#elif TARGET_OS_MAC
#define OPENVPN_PLATFORM_MAC
#endif
#elif defined(__ANDROID__)
#define OPENVPN_PLATFORM_ANDROID
#elif defined(__linux__)
#define OPENVPN_PLATFORM_LINUX
#endif

#if !defined(_WIN32)
#define OPENVPN_PLATFORM_TYPE_UNIX
#endif

#endif // OPENVPN_COMMON_PLATFORM_H
