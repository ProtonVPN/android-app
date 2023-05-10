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

#ifndef OPENVPN_CRYPTO_TOKENENCRYPT_H
#define OPENVPN_CRYPTO_TOKENENCRYPT_H

#include <string>
#include <atomic>
#include <cstdint> // for std::uint8_t

#include <openssl/evp.h>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/base64.hpp>
#include <openvpn/buffer/buffer.hpp>
#include <openvpn/random/randapi.hpp>
#include <openvpn/openssl/util/error.hpp>

#include <openvpn/openssl/compat.hpp>

namespace openvpn {
class TokenEncrypt
{
  public:
    class Key
    {
      public:
        static constexpr size_t SIZE = 16;

        Key(RandomAPI &rng)
        {
            rng.assert_crypto();
            rng.rand_bytes(data, sizeof(data));
        }

      private:
        friend class TokenEncrypt;
        std::uint8_t data[SIZE];
    };

    // mode parameter for constructor
    enum
    {
        ENCRYPT = 1,
        DECRYPT = 0
    };

    TokenEncrypt(const Key &key, const int mode)
    {
        ctx = EVP_CIPHER_CTX_new();
        EVP_CIPHER_CTX_reset(ctx);
        if (!EVP_CipherInit_ex(ctx, EVP_aes_128_ecb(), nullptr, key.data, nullptr, mode))
        {
            EVP_CIPHER_CTX_free(ctx);
            throw OpenSSLException("TokenEncrypt: EVP_CipherInit_ex[1] failed");
        }
        EVP_CIPHER_CTX_set_padding(ctx, 0);
    }

    ~TokenEncrypt()
    {
        EVP_CIPHER_CTX_free(ctx);
    }

    // Do the encrypt/decrypt
    void operator()(std::uint8_t *dest, const std::uint8_t *src, const int size)
    {
        // NOTE: since this algorithm uses the ECB block cipher mode,
        // it should only be used to encrypt/decrypt a message which
        // is exactly equal to the AES block size (16 bytes).
        if (size != EVP_CIPHER_CTX_block_size(ctx))
            throw Exception("TokenEncrypt: encrypt/decrypt data must be equal to AES block size");
        int outlen = 0;
        if (!EVP_CipherInit_ex(ctx, nullptr, nullptr, nullptr, nullptr, -1))
            throw OpenSSLException("TokenEncrypt: EVP_CipherInit_ex[2] failed");
        if (!EVP_CipherUpdate(ctx, dest, &outlen, src, size))
            throw OpenSSLException("TokenEncrypt: EVP_CipherUpdate failed");
        // NOTE: we skip EVP_CipherFinal_ex because we are running in ECB mode without padding
        if (outlen != size)
            throw Exception("TokenEncrypt: unexpected output length=" + std::to_string(outlen) + " expected=" + std::to_string(size));
    }

  private:
    TokenEncrypt(const TokenEncrypt &) = delete;
    TokenEncrypt &operator=(const TokenEncrypt &) = delete;

    EVP_CIPHER_CTX *ctx;
};

struct TokenEncryptDecrypt
{
    TokenEncryptDecrypt(const TokenEncrypt::Key &key)
        : encrypt(key, TokenEncrypt::ENCRYPT),
          decrypt(key, TokenEncrypt::DECRYPT)
    {
    }

    TokenEncrypt encrypt;
    TokenEncrypt decrypt;
};
} // namespace openvpn

#endif
