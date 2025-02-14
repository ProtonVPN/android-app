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

// Wrap the mbed TLS cipher API defined in <mbedtls/cipher.h> so
// that it can be used as part of the crypto layer of the OpenVPN core.

#ifndef OPENVPN_MBEDTLS_CRYPTO_CIPHER_H
#define OPENVPN_MBEDTLS_CRYPTO_CIPHER_H

#include <string>

#include <mbedtls/cipher.h>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/crypto/static_key.hpp>
#include <openvpn/crypto/cryptoalgs.hpp>

namespace openvpn::MbedTLSCrypto {
class CipherContextCommon
{
  public:
    OPENVPN_SIMPLE_EXCEPTION(mbedtls_cipher_mode_error);
    OPENVPN_SIMPLE_EXCEPTION(mbedtls_cipher_uninitialized);

    // mode parameter for constructor
    enum
    {
        MODE_UNDEF = MBEDTLS_OPERATION_NONE,
        ENCRYPT = MBEDTLS_ENCRYPT,
        DECRYPT = MBEDTLS_DECRYPT
    };

    /**
     * This crypto library requires the auth tag to be at the end for
     * encryption and decryption
     * @return
     */
    bool constexpr requires_authtag_at_end()
    {
        return true;
    }

  protected:
    CipherContextCommon() = default;

    virtual ~CipherContextCommon()
    {
        erase();
    }

    CipherContextCommon(const CipherContextCommon &other) = delete;
    CipherContextCommon &operator=(const CipherContextCommon &other) = delete;


    CipherContextCommon(CipherContextCommon &&other) noexcept
    {
        ctx = other.ctx;
        initialized = other.initialized;
        other.ctx = {};
        other.initialized = false;
    }

    CipherContextCommon &operator=(CipherContextCommon &&other)
    {
        ctx = other.ctx;
        initialized = other.initialized;
        other.ctx = {};
        other.initialized = false;
        return *this;
    }

    static void check_mode(int mode)
    {
        // check that mode is valid
        if (!(mode == ENCRYPT || mode == DECRYPT))
            throw mbedtls_cipher_mode_error();
    }

    void erase()
    {
        if (initialized)
        {
            mbedtls_cipher_free(&ctx);
            initialized = false;
        }
    }

    void check_initialized() const
    {
        if (unlikely(!initialized))
            throw mbedtls_cipher_uninitialized();
    }

    bool initialized = false;
    mbedtls_cipher_context_t ctx;
};

class CipherContext
    : public CipherContextCommon
{
    CipherContext(const CipherContext &) = delete;
    CipherContext &operator=(const CipherContext &) = delete;

  public:
    OPENVPN_EXCEPTION(mbedtls_cipher_error);

    // mode parameter for constructor
    enum
    {
        MODE_UNDEF = MBEDTLS_OPERATION_NONE,
        ENCRYPT = MBEDTLS_ENCRYPT,
        DECRYPT = MBEDTLS_DECRYPT
    };

    // mbed TLS cipher constants
    enum
    {
        MAX_IV_LENGTH = MBEDTLS_MAX_IV_LENGTH,
        CIPH_CBC_MODE = MBEDTLS_MODE_CBC
    };

    CipherContext() = default;

    ~CipherContext()
    {
        erase();
    }

    static bool is_supported(SSLLib::Ctx libctx, const CryptoAlgs::Type alg)
    {
        return (cipher_type(alg) != nullptr);
    }

    void init(SSLLib::Ctx libctx,
              const CryptoAlgs::Type alg,
              const unsigned char *key,
              const int mode)
    {
        erase();

        check_mode(mode);

        // get cipher type
        const mbedtls_cipher_info_t *ci = cipher_type(alg);
        if (!ci)
            OPENVPN_THROW(mbedtls_cipher_error, CryptoAlgs::name(alg) << ": not usable");

        // initialize cipher context with cipher type
        if (mbedtls_cipher_setup(&ctx, ci) < 0)
            throw mbedtls_cipher_error("mbedtls_cipher_setup");

        // set key and encrypt/decrypt mode
        if (mbedtls_cipher_setkey(&ctx, key, mbedtls_cipher_get_key_bitlen(&ctx), (mbedtls_operation_t)mode) < 0)
            throw mbedtls_cipher_error("mbedtls_cipher_setkey");

        initialized = true;
    }

    void reset(const unsigned char *iv)
    {
        check_initialized();
        if (mbedtls_cipher_reset(&ctx) < 0)
            throw mbedtls_cipher_error("mbedtls_cipher_reset");
        if (mbedtls_cipher_set_iv(&ctx, iv, iv_length()))
            throw mbedtls_cipher_error("mbedtls_cipher_set_iv");
    }

    bool update(unsigned char *out,
                const size_t max_out_size,
                const unsigned char *in,
                const size_t in_size,
                size_t &out_acc)
    {
        check_initialized();
        size_t outlen;
        if (mbedtls_cipher_update(&ctx, in, in_size, out, &outlen) >= 0)
        {
            out_acc += outlen;
            return true;
        }
        else
            return false;
    }

    bool final(unsigned char *out, const size_t max_out_size, size_t &out_acc)
    {
        check_initialized();
        size_t outlen;
        if (mbedtls_cipher_finish(&ctx, out, &outlen) >= 0)
        {
            out_acc += outlen;
            return true;
        }
        else
            return false;
    }

    bool is_initialized() const
    {
        return initialized;
    }

    size_t iv_length() const
    {
        check_initialized();
        return mbedtls_cipher_get_iv_size(&ctx);
    }

    size_t block_size() const
    {
        check_initialized();
        return mbedtls_cipher_get_block_size(&ctx);
    }

    // return cipher mode (such as CIPH_CBC_MODE, etc.)
    int cipher_mode() const
    {
        check_initialized();
        return mbedtls_cipher_get_cipher_mode(&ctx);
    }

  private:
    static const mbedtls_cipher_info_t *cipher_type(const CryptoAlgs::Type alg)
    {
        switch (alg)
        {
        case CryptoAlgs::AES_128_CBC:
            return mbedtls_cipher_info_from_type(MBEDTLS_CIPHER_AES_128_CBC);
        case CryptoAlgs::AES_192_CBC:
            return mbedtls_cipher_info_from_type(MBEDTLS_CIPHER_AES_192_CBC);
        case CryptoAlgs::AES_256_CBC:
            return mbedtls_cipher_info_from_type(MBEDTLS_CIPHER_AES_256_CBC);
        case CryptoAlgs::AES_256_CTR:
            return mbedtls_cipher_info_from_type(MBEDTLS_CIPHER_AES_256_CTR);
        case CryptoAlgs::DES_CBC:
            return mbedtls_cipher_info_from_type(MBEDTLS_CIPHER_DES_CBC);
        case CryptoAlgs::DES_EDE3_CBC:
            return mbedtls_cipher_info_from_type(MBEDTLS_CIPHER_DES_EDE3_CBC);
#if MBEDTLS_VERSION_NUMBER < 0x03000000
            /* no longer supported in newer mbed TLS versions */
        case CryptoAlgs::BF_CBC:
            return mbedtls_cipher_info_from_type(MBEDTLS_CIPHER_BLOWFISH_CBC);
#endif
        default:
            return nullptr;
        }
    }
};
} // namespace openvpn::MbedTLSCrypto

#endif
