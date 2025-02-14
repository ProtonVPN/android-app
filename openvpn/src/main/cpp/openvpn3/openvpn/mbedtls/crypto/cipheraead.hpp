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

// Wrap the mbed TLS AEAD API.

#pragma once

#include <string>

#include <mbedtls/gcm.h>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/likely.hpp>
#include <openvpn/crypto/static_key.hpp>
#include <openvpn/crypto/cryptoalgs.hpp>
#include <openvpn/crypto/aead_usage_limit.hpp>
#include <openvpn/mbedtls/crypto/cipher.hpp>

namespace openvpn::MbedTLSCrypto {
class CipherContextAEAD : public CipherContextCommon
{
  public:
    OPENVPN_EXCEPTION(mbedtls_aead_error);


    // mbed TLS cipher constants
    enum
    {
        IV_LEN = 12,
        AUTH_TAG_LEN = 16
    };

#if 0
      // mbed TLS encrypt/decrypt return values
      enum {
	GCM_AUTH_FAILED = MBEDTLS_ERR_GCM_AUTH_FAILED,
	SUCCESS = 0,
      };
#endif

    CipherContextAEAD() = default;

    ~CipherContextAEAD()
    {
        erase();
    }

    CipherContextAEAD(CipherContextAEAD &&other) noexcept
        : CipherContextCommon(std::move(other)), aead_usage_limit_(other.aead_usage_limit_)
    {
    }

    CipherContextAEAD &operator=(CipherContextAEAD &&other)
    {
        CipherContextAEAD temp(std::move(other));
        ctx = temp.ctx;
        initialized = temp.initialized;
        temp.ctx = {};
        temp.initialized = false;
        aead_usage_limit_ = temp.aead_usage_limit_;
        return *this;
    }


    void init(SSLLib::Ctx libctx,
              const CryptoAlgs::Type alg,
              const unsigned char *key,
              const unsigned int keysize,
              const int mode)
    {
        erase();

        check_mode(mode);

        // get cipher type
        unsigned int ckeysz = 0;
        const mbedtls_cipher_type_t cid = cipher_type(alg, ckeysz);
        if (cid == MBEDTLS_CIPHER_NONE)
            OPENVPN_THROW(mbedtls_aead_error, CryptoAlgs::name(alg) << ": not usable");

        if (ckeysz > keysize)
            throw mbedtls_aead_error("insufficient key material");

        auto *ci = mbedtls_cipher_info_from_type(cid);

        // initialize cipher context with cipher type
        if (mbedtls_cipher_setup(&ctx, ci) < 0)
            throw mbedtls_aead_error("mbedtls_cipher_setup");

        if (mbedtls_cipher_setkey(&ctx, key, ckeysz * 8, (mbedtls_operation_t)mode) < 0)
            throw mbedtls_aead_error("mbedtls_cipher_setkey");

        aead_usage_limit_ = {alg};
        initialized = true;
    }



    void encrypt(const unsigned char *input,
                 unsigned char *output,
                 size_t length,
                 const unsigned char *iv,
                 unsigned char *tag,
                 const unsigned char *ad,
                 size_t ad_len)
    {
        check_initialized();
        const int status = mbedtls_cipher_auth_encrypt_ext(&ctx,
                                                           iv,
                                                           IV_LEN,
                                                           ad,
                                                           ad_len,
                                                           input,
                                                           length,
                                                           output,
                                                           length + AUTH_TAG_LEN,
                                                           &length,
                                                           AUTH_TAG_LEN);
        if (unlikely(status))
            OPENVPN_THROW(mbedtls_aead_error, "mbedtls_cipher_auth_encrypt failed with status=" << status);
        aead_usage_limit_.update(length + ad_len);
    }

    /** Returns the AEAD usage limit associated with this AEAD cipher instance to check the limits */
    [[nodiscard]] const Crypto::AEADUsageLimit &get_usage_limit()
    {
        return aead_usage_limit_;
    }

    /**
     * Decrypts AEAD encrypted data. Note that this method ignores the tag parameter
     * and the tag is assumed to be part of input and at the end of the input.
     *
     * @param input     Input data to decrypt
     * @param output    Where decrypted data will be written to
     * @param iv        IV of the encrypted data.
     * @param length    length the of the data, this includes the tag at the end.
     * @param ad        start of the additional data
     * @param ad_len    length of the additional data
     * @param tag       ignored by the mbed TLS variant of the method. (see OpenSSL variant of the method for more details).
     *
     * input and output may NOT be equal
     */
    bool decrypt(const unsigned char *input,
                 unsigned char *output,
                 size_t length,
                 const unsigned char *iv,
                 const unsigned char *tag,
                 const unsigned char *ad,
                 size_t ad_len)
    {
        check_initialized();

        if (unlikely(tag != nullptr))
        {
            /* If we are called with a non-null tag, the function is not going to be able to decrypt */
            throw mbedtls_aead_error("tag must be null for aead decrypt");
        }

        size_t olen;
        const int status = mbedtls_cipher_auth_decrypt_ext(&ctx,
                                                           iv,
                                                           IV_LEN,
                                                           ad,
                                                           ad_len,
                                                           input,
                                                           length,
                                                           output,
                                                           length - AUTH_TAG_LEN,
                                                           &olen,
                                                           AUTH_TAG_LEN);

        return (olen == length - AUTH_TAG_LEN) && (status == 0);
    }

    bool is_initialized() const
    {
        return initialized;
    }

    static bool is_supported(void *libctx, const CryptoAlgs::Type alg)
    {
        unsigned int keysize;
        return (cipher_type(alg, keysize) != MBEDTLS_CIPHER_NONE);
    }

  private:
    Crypto::AEADUsageLimit aead_usage_limit_ = {};
    static mbedtls_cipher_type_t cipher_type(const CryptoAlgs::Type alg, unsigned int &keysize)
    {
        switch (alg)
        {
        case CryptoAlgs::AES_128_GCM:
            keysize = 16;
            return MBEDTLS_CIPHER_AES_128_GCM;
        case CryptoAlgs::AES_192_GCM:
            keysize = 24;
            return MBEDTLS_CIPHER_AES_192_GCM;
        case CryptoAlgs::AES_256_GCM:
            keysize = 32;
            return MBEDTLS_CIPHER_AES_256_GCM;
#ifdef MBEDTLS_CHACHAPOLY_C
        case CryptoAlgs::CHACHA20_POLY1305:
            keysize = 32;
            return MBEDTLS_CIPHER_CHACHA20_POLY1305;
#endif
        default:
            keysize = 0;
            return MBEDTLS_CIPHER_NONE;
        }
    }
};
} // namespace openvpn::MbedTLSCrypto
