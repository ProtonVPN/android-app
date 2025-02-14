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

// define very basic types such as size_t, ssize_t

#ifndef OPENVPN_COMMON_SIZE_H
#define OPENVPN_COMMON_SIZE_H

#include <cstddef> // defines std::size_t

#include <openvpn/common/platform.hpp>

#ifdef OPENVPN_PLATFORM_WIN
#if !defined(_SSIZE_T_) && !defined(_SSIZE_T_DEFINED) && !defined(HAVE_SSIZE_T)
#include <BaseTsd.h>
typedef SSIZE_T ssize_t;
#define _SSIZE_T_
#define _SSIZE_T_DEFINED
#endif
#else
#include <unistd.h> // get ssize_t
#endif

#endif
