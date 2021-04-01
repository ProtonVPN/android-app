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

#ifndef OPENVPN_MBEDTLS_CRYPTO_API_H
#define OPENVPN_MBEDTLS_CRYPTO_API_H

#include <openvpn/mbedtls/crypto/cipher.hpp>
#include <openvpn/mbedtls/crypto/cipheraead.hpp>
#include <openvpn/mbedtls/crypto/digest.hpp>
#include <openvpn/mbedtls/crypto/hmac.hpp>

namespace openvpn {

  // type container for MbedTLS Crypto-level API
  struct MbedTLSCryptoAPI {
    // cipher
    typedef MbedTLSCrypto::CipherContext CipherContext;
    typedef MbedTLSCrypto::CipherContextAEAD CipherContextAEAD;

    // digest
    typedef MbedTLSCrypto::DigestContext DigestContext;

    // HMAC
    typedef MbedTLSCrypto::HMACContext HMACContext;
  };
}

#endif
