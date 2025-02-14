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

// A general purpose container for OpenVPN protocol encrypt and decrypt objects.

#ifndef OPENVPN_CRYPTO_SELFTEST_H
#define OPENVPN_CRYPTO_SELFTEST_H

#include <string>

#ifdef USE_OPENSSL
// #include <openvpn/openssl/util/selftest.hpp>
#endif

#ifdef USE_APPLE_SSL
// #include <openvpn/applecrypto/util/selftest.hpp>
#endif

#ifdef USE_MBEDTLS
#include <openvpn/mbedtls/util/selftest.hpp>
#endif

#ifdef USE_MBEDTLS_APPLE_HYBRID
// #include <openvpn/applecrypto/util/selftest.hpp>
#include <openvpn/mbedtls/util/selftest.hpp>
#endif

namespace openvpn::SelfTest {
inline std::string crypto_self_test()
{
    std::string ret;
#ifdef USE_OPENSSL
    // ret += crypto_self_test_openssl();
#endif
#ifdef USE_APPLE_SSL
    // ret += crypto_self_test_apple();
#endif
#if defined(USE_MBEDTLS) || defined(USE_MBEDTLS_APPLE_HYBRID)
    ret += crypto_self_test_mbedtls();
#endif
    return ret;
}
} // namespace openvpn::SelfTest

#endif // OPENVPN_CRYPTO_CRYPTO_H
