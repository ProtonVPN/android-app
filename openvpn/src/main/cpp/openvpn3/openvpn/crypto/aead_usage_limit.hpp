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

#ifndef OPENVPN_CRYPTO_CRYPTO_AEAD_USAGE_LIMIT_H
#define OPENVPN_CRYPTO_CRYPTO_AEAD_USAGE_LIMIT_H

#include <cstdint>
#include "openvpn/crypto/cryptoalgs.hpp"

namespace openvpn::Crypto {

/** Counts the number of plaintext blocks and cipher invocations to determine the limit for an AEAD cipher like GCM
 * For more details see the OpenVPN RFC and the related documents */
class AEADUsageLimit
{
    uint64_t invocations = 0;
    uint64_t plaintext_blocks = 0;
    /** Usage limit of plaintext_block + invocations, initialise with AES-GCM limit to be on safe side */
    uint64_t limit = openvpn::CryptoAlgs::gcm_limit;

  public:
    AEADUsageLimit() = default;

    AEADUsageLimit(openvpn::CryptoAlgs::Type type)
        : limit(openvpn::CryptoAlgs::aead_usage_limit(type))
    {
    }

    /* Since cipher_ctx_block_size() of OpenSSL is not  reliable and will return 1 in many
     * cases use a hardcoded blocksize instead. This is technically false for Chacha20-Poly1305 but
     * Chacha20-Poly1305 also does not need the limit currently*/
    static constexpr size_t aead_blocksize = 16;

    /** Update the limit calculation with the amount of data encrypted */
    void update(const std::size_t outlen)
    {
        /* update number of plaintext blocks encrypted. Use the x + (n-1)/n trick
         * to round up the result to the number of blocked used */
        plaintext_blocks += (outlen + (aead_blocksize - 1)) / aead_blocksize;
        invocations++;
    }

    /** Returns true if the limit that is considered for the usage of the AEAD ciphers has been reached */
    [[nodiscard]] bool usage_limit_reached() const
    {
        if (limit == 0)
            return false;

        return plaintext_blocks + invocations > limit;
    }

    /** Returns true if we are 7/8 of the usage limit. We use this limit to trigger a renegotiation */
    [[nodiscard]] bool usage_limit_warn() const
    {
        if (limit == 0)
            return false;

        return plaintext_blocks + invocations > limit / 8 * 7;
    }
};
} // namespace openvpn::Crypto

#endif