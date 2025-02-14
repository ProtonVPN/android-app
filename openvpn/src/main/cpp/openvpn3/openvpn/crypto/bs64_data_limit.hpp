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

// Special data limits on Blowfish, Triple DES, and other 64-bit
// block-size ciphers vulnerable to "Sweet32" birthday attack
// (CVE-2016-6329).  Limit such cipher keys to no more than 64 MB
// of data encrypted/decrypted.  Note that we trigger early at
// 48 MB to compensate for possible delays in renegotiation and
// rollover to the new key.

#ifndef OPENVPN_CRYPTO_DATALIMIT_H
#define OPENVPN_CRYPTO_DATALIMIT_H

#include <openvpn/crypto/cryptoalgs.hpp>

#ifndef OPENVPN_BS64_DATA_LIMIT
#define OPENVPN_BS64_DATA_LIMIT 48000000
#endif

namespace openvpn {
inline bool is_bs64_cipher(const CryptoAlgs::Type cipher)
{
    return CryptoAlgs::get(cipher).block_size() == 8;
}
} // namespace openvpn

#endif
