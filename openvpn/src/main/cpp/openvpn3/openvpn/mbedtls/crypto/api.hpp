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

#ifndef OPENVPN_MBEDTLS_CRYPTO_API_H
#define OPENVPN_MBEDTLS_CRYPTO_API_H

#include <openvpn/mbedtls/crypto/cipher.hpp>
#include <openvpn/mbedtls/crypto/cipheraead.hpp>
#include <openvpn/mbedtls/crypto/digest.hpp>
#include <openvpn/mbedtls/crypto/hmac.hpp>
#include <openvpn/mbedtls/crypto/tls1prf.hpp>

namespace openvpn {

// type container for MbedTLS Crypto-level API
struct MbedTLSCryptoAPI
{
    // cipher
    typedef MbedTLSCrypto::CipherContext CipherContext;
    typedef MbedTLSCrypto::CipherContextAEAD CipherContextAEAD;

    // digest
    typedef MbedTLSCrypto::DigestContext DigestContext;

    // HMAC
    typedef MbedTLSCrypto::HMACContext HMACContext;

    // TLS 1.0/1.1 PRF function
    using TLS1PRF = MbedTLSCrypto::TLS1PRF;
};
} // namespace openvpn

#endif
