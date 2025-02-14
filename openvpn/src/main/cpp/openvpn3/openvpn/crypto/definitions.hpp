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

#ifdef USE_OPENSSL
#include <openssl/opensslv.h>
#include <openssl/crypto.h>
#endif

/* We need to define this very early and in its own small header file so we
 * can ensure that these definitions are always available */
namespace openvpn::SSLLib {

#if defined(USE_OPENSSL) && OPENSSL_VERSION_NUMBER >= 0x30000000L
using Ctx = OSSL_LIB_CTX *;
#else
using Ctx = void *;
#endif

} // namespace openvpn::SSLLib
