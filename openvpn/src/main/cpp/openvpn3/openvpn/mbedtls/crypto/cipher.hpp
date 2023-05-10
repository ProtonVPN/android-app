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
#include <openvpn/ssl/sslapi.hpp>

namespace openvpn {
namespace MbedTLSCrypto {
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
        if (mbedtls_cipher_setkey(&ctx, key, ci->key_bitlen, (mbedtls_operation_t)mode) < 0)
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
        case CryptoAlgs::BF_CBC:
            return mbedtls_cipher_info_from_type(MBEDTLS_CIPHER_BLOWFISH_CBC);
        default:
            return nullptr;
        }
    }
};
} // namespace MbedTLSCrypto
} // namespace openvpn

#endif
