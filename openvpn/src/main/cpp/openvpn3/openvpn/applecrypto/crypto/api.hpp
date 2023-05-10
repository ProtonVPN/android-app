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
