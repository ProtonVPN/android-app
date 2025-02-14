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

#ifndef OPENVPN_CRYPTO_CRYPTO_CHOOSE_H
#define OPENVPN_CRYPTO_CRYPTO_CHOOSE_H

#include <openvpn/crypto/definitions.hpp>

#ifdef USE_OPENSSL
#include <openvpn/openssl/crypto/api.hpp>
#include <openvpn/openssl/util/rand.hpp>
#endif

#ifdef USE_APPLE_SSL
#include <openvpn/applecrypto/crypto/api.hpp>
#include <openvpn/applecrypto/util/rand.hpp>
#endif

#ifdef USE_MBEDTLS
#include <mbedtls/platform.h>
#include <mbedtls/debug.h> // for debug_set_threshold
#include <openvpn/mbedtls/crypto/api.hpp>
#include <openvpn/mbedtls/util/rand.hpp>
#ifdef OPENVPN_PLATFORM_UWP
#include <openvpn/mbedtls/util/uwprand.hpp>
#endif
#endif

#ifdef USE_MBEDTLS_APPLE_HYBRID
#include <openvpn/applecrypto/crypto/api.hpp>
#include <openvpn/mbedtls/util/rand.hpp>
#endif

namespace openvpn::SSLLib {
#if defined(USE_MBEDTLS)
#define SSL_LIB_NAME "MbedTLS"
using CryptoAPI = MbedTLSCryptoAPI;
#if defined OPENVPN_PLATFORM_UWP
using RandomAPI = MbedTLSRandomWithUWPEntropy;
#else
using RandomAPI = MbedTLSRandom;
#endif
#elif defined(USE_MBEDTLS_APPLE_HYBRID)
// Uses Apple framework for CryptoAPI and MbedTLS for SSLAPI and RandomAPI
#define SSL_LIB_NAME "MbedTLSAppleHybrid"
using CryptoAPI = AppleCryptoAPI;
using RandomAPI = MbedTLSRandom;
#elif defined(USE_APPLE_SSL)
#define SSL_LIB_NAME "AppleSSL"
typedef AppleCryptoAPI CryptoAPI;
typedef AppleRandom RandomAPI;
#elif defined(USE_OPENSSL)
#define SSL_LIB_NAME "OpenSSL"
using CryptoAPI = OpenSSLCryptoAPI;
using RandomAPI = OpenSSLRandom;
#else
#error no SSL library defined
#endif
} // namespace openvpn::SSLLib

#endif
