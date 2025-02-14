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

#ifndef OPENVPN_OPENSSL_CRYPTO_API_H
#define OPENVPN_OPENSSL_CRYPTO_API_H

#include <openvpn/openssl/crypto/cipher.hpp>
#include <openvpn/openssl/crypto/cipheraead.hpp>
#include <openvpn/openssl/crypto/digest.hpp>
#include <openvpn/openssl/crypto/mac.hpp>
#include <openvpn/openssl/crypto/tls1prf.hpp>
#include "tls1prf.hpp"

namespace openvpn {

// type container for OpenSSL Crypto-level API
struct OpenSSLCryptoAPI
{
    // cipher
    typedef OpenSSLCrypto::CipherContext CipherContext;
    typedef OpenSSLCrypto::CipherContextAEAD CipherContextAEAD;

    // digest
    typedef OpenSSLCrypto::DigestContext DigestContext;

    // HMAC
    typedef OpenSSLCrypto::HMACContext HMACContext;

    // TLS 1.0/1.1 PRF function
    using TLS1PRF = OpenSSLCrypto::TLS1PRF;
};
} // namespace openvpn

#endif
