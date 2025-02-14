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

// Wrap the mbed TLS HMAC API defined in <mbedtls/md.h> so
// that it can be used as part of the crypto layer of the OpenVPN core.

#ifndef OPENVPN_MBEDTLS_CRYPTO_HMAC_H
#define OPENVPN_MBEDTLS_CRYPTO_HMAC_H

#include <string>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/mbedtls/crypto/digest.hpp>
#include <openvpn/mbedtls/mbedtls_compat.hpp>

namespace openvpn::MbedTLSCrypto {
class HMACContext
{
    HMACContext(const HMACContext &) = delete;
    HMACContext &operator=(const HMACContext &) = delete;

  public:
    OPENVPN_SIMPLE_EXCEPTION(mbedtls_hmac_uninitialized);
    OPENVPN_EXCEPTION(mbedtls_hmac_error);

    enum
    {
        MAX_HMAC_SIZE = MBEDTLS_MD_MAX_SIZE
    };

    HMACContext()
        : initialized(false)
    {
    }

    HMACContext(const CryptoAlgs::Type digest, const unsigned char *key, const size_t key_size)
        : initialized(false)
    {
        init(digest, key, key_size);
    }

    ~HMACContext()
    {
        erase();
    }

    void init(const CryptoAlgs::Type digest, const unsigned char *key, const size_t key_size)
    {
        erase();

        mbedtls_md_init(&ctx);
        if (mbedtls_md_setup(&ctx, DigestContext::digest_type(digest), 1) < 0)
            throw mbedtls_hmac_error("mbedtls_md_setup");
        if (mbedtls_md_hmac_starts(&ctx, key, key_size) < 0)
            throw mbedtls_hmac_error("mbedtls_md_hmac_starts");
        initialized = true;
    }

    void reset()
    {
        check_initialized();
        if (mbedtls_md_hmac_reset(&ctx) < 0)
            throw mbedtls_hmac_error("mbedtls_md_hmac_reset");
    }

    void update(const unsigned char *in, const size_t size)
    {
        check_initialized();
        if (mbedtls_md_hmac_update(&ctx, in, size) < 0)
            throw mbedtls_hmac_error("mbedtls_md_hmac_update");
    }

    size_t final(unsigned char *out)
    {
        check_initialized();
        if (mbedtls_md_hmac_finish(&ctx, out) < 0)
            throw mbedtls_hmac_error("mbedtls_md_hmac_finish");
        return size_();
    }

    size_t size() const
    {
        check_initialized();
        return size_();
    }

    bool is_initialized() const
    {
        return initialized;
    }

  private:
    void erase()
    {
        if (initialized)
        {
            mbedtls_md_free(&ctx);
            initialized = false;
        }
    }

    size_t size_() const
    {
        return mbedtls_md_get_size(mbedtls_md_info_from_ctx(&ctx));
    }

    void check_initialized() const
    {
#ifdef OPENVPN_ENABLE_ASSERT
        if (!initialized)
            throw mbedtls_hmac_uninitialized();
#endif
    }

    bool initialized;
    mbedtls_md_context_t ctx;
};
} // namespace openvpn::MbedTLSCrypto

#endif
