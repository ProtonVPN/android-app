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

#ifndef OPENVPN_APPLECRYPTO_CRYPTO_API_H
#define OPENVPN_APPLECRYPTO_CRYPTO_API_H

#include <openvpn/applecrypto/crypto/cipher.hpp>
#include <openvpn/applecrypto/crypto/ciphergcm.hpp>
#include <openvpn/applecrypto/crypto/digest.hpp>
#include <openvpn/applecrypto/crypto/hmac.hpp>

namespace openvpn {

// type container for Apple Crypto-level API
struct AppleCryptoAPI
{
    // cipher
    typedef AppleCrypto::CipherContext CipherContext;
    typedef AppleCrypto::CipherContextAEAD CipherContextAEAD;

    // digest
    typedef AppleCrypto::DigestContext DigestContext;

    // HMAC
    typedef AppleCrypto::HMACContext HMACContext;
};
} // namespace openvpn

#endif
