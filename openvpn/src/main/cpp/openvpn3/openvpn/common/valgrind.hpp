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

#pragma once

#if defined(HAVE_VALGRIND)
#include <valgrind/memcheck.h>
#define OPENVPN_MAKE_MEM_DEFINED(addr, len) VALGRIND_MAKE_MEM_DEFINED(addr, len)
#else
#define OPENVPN_MAKE_MEM_DEFINED(addr, len)
#endif
