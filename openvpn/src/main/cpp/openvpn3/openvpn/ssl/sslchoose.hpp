//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2020 OpenVPN Inc.
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

#ifndef OPENVPN_SSL_SSLCHOOSE_H
#define OPENVPN_SSL_SSLCHOOSE_H

#include <openvpn/crypto/definitions.hpp>

#ifdef USE_OPENSSL
#include <openvpn/openssl/crypto/api.hpp>
#include <openvpn/openssl/ssl/sslctx.hpp>
#include <openvpn/openssl/util/rand.hpp>
#include <openvpn/openssl/util/pem.hpp>
#endif

#ifdef USE_APPLE_SSL
#include <openvpn/applecrypto/crypto/api.hpp>
#include <openvpn/applecrypto/ssl/sslctx.hpp>
#include <openvpn/applecrypto/util/rand.hpp>
#endif

#ifdef USE_MBEDTLS
#include <mbedtls/platform.h>
#include <mbedtls/debug.h>  // for debug_set_threshold
#include <openvpn/mbedtls/crypto/api.hpp>
#include <openvpn/mbedtls/ssl/sslctx.hpp>
#include <openvpn/mbedtls/util/rand.hpp>
#ifdef OPENVPN_PLATFORM_UWP
#include <openvpn/mbedtls/util/uwprand.hpp>
#endif
#include <openvpn/mbedtls/util/pem.hpp>
#endif

#ifdef USE_MBEDTLS_APPLE_HYBRID
#include <openvpn/applecrypto/crypto/api.hpp>
#include <openvpn/mbedtls/ssl/sslctx.hpp>
#include <openvpn/mbedtls/util/rand.hpp>
#endif

namespace openvpn {
  namespace SSLLib {
#if defined(USE_MBEDTLS)
#define SSL_LIB_NAME "MbedTLS"
    typedef MbedTLSCryptoAPI CryptoAPI;
    typedef MbedTLSContext SSLAPI;
#if defined OPENVPN_PLATFORM_UWP
    typedef MbedTLSRandomWithUWPEntropy RandomAPI;
#else
    typedef MbedTLSRandom RandomAPI;
#endif
    typedef MbedTLSPEM PEMAPI;
#elif defined(USE_MBEDTLS_APPLE_HYBRID)
    // Uses Apple framework for CryptoAPI and MbedTLS for SSLAPI and RandomAPI
#define SSL_LIB_NAME "MbedTLSAppleHybrid"
    typedef AppleCryptoAPI CryptoAPI;
    typedef MbedTLSContext SSLAPI;
    typedef MbedTLSRandom RandomAPI;
#elif defined(USE_APPLE_SSL)
#define SSL_LIB_NAME "AppleSSL"
    typedef AppleCryptoAPI CryptoAPI;
    typedef AppleSSLContext SSLAPI;
    typedef AppleRandom RandomAPI;
#elif defined(USE_OPENSSL)
#define SSL_LIB_NAME "OpenSSL"
    typedef OpenSSLCryptoAPI CryptoAPI;
    typedef OpenSSLContext SSLAPI;
    typedef OpenSSLRandom RandomAPI;
    typedef OpenSSLPEM PEMAPI;
#else
#error no SSL library defined
#endif
  }
}

#endif
