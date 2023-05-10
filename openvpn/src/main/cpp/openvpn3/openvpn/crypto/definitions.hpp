//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2022 OpenVPN Inc.
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU Affero General Public License Version 3
//    as published by the Free Software Foundation.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU Affero General Public License for more details.
//
//    You should have received a copy of the GNU Affero General Public License
//    along with this program in the COPYING file.
//    If not, see <http://www.gnu.org/licenses/>.

#pragma once

#if USE_OPENSSL
#include <openssl/opensslv.h>
#include <openssl/crypto.h>
#endif

/* We need to define this very early and in its own small header file so we
 * can ensure that these definitions are always available */
namespace openvpn {
namespace SSLLib {

#if defined(USE_OPENSSL) && OPENSSL_VERSION_NUMBER >= 0x30000000L
using Ctx = OSSL_LIB_CTX *;
#else
using Ctx = void *;
#endif

} // namespace SSLLib
} // namespace openvpn
