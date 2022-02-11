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

// Wrap the OpenSSL cipher API defined in <openssl/evp.h> so
// that it can be used as part of the crypto layer of the OpenVPN core.

#ifndef OPENVPN_OPENSSL_CRYPTO_CIPHER_H
#define OPENVPN_OPENSSL_CRYPTO_CIPHER_H

#include <string>

#include <openssl/objects.h>
#include <openssl/evp.h>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/crypto/static_key.hpp>
#include <openvpn/crypto/cryptoalgs.hpp>
#include <openvpn/openssl/util/error.hpp>
#include <openvpn/openssl/compat.hpp>

namespace openvpn {
  namespace OpenSSLCrypto {
    class CipherContext
    {
      /* In OpenSSL 3.0 the method that returns EVP_CIPHER, the cipher needs to be
       * freed afterwards, thus needing a non-const type. In contrast, OpenSSL 1.1.1
       * and lower returns a const type, needing a const type */
#if OPENSSL_VERSION_NUMBER < 0x30000000L
    using evp_cipher_type = const EVP_CIPHER;
#else
    using evp_cipher_type = EVP_CIPHER;
#endif

      using CIPHER_unique_ptr = std::unique_ptr<evp_cipher_type , decltype(&::EVP_CIPHER_free)>;

      CipherContext(const CipherContext&) = delete;
      CipherContext& operator=(const CipherContext&) = delete;

    public:
      OPENVPN_SIMPLE_EXCEPTION(openssl_cipher_mode_error);
      OPENVPN_SIMPLE_EXCEPTION(openssl_cipher_uninitialized);
      OPENVPN_EXCEPTION(openssl_cipher_error);

      // mode parameter for constructor
      enum {
	MODE_UNDEF = -1,
	ENCRYPT = 1,
	DECRYPT = 0
      };

      // OpenSSL cipher constants
      enum {
	MAX_IV_LENGTH = EVP_MAX_IV_LENGTH,
	CIPH_CBC_MODE = EVP_CIPH_CBC_MODE
      };

      CipherContext() = default;

      ~CipherContext() { free_cipher_context() ; }

      static bool is_supported(SSLLib::Ctx libctx, const CryptoAlgs::Type alg)
      {
	CIPHER_unique_ptr cipher(cipher_type(libctx, alg), EVP_CIPHER_free);
	return (bool)(cipher);
      }

      void init(SSLLib::Ctx libctx, const CryptoAlgs::Type alg, const unsigned char *key, const int mode)
      {
	// check that mode is valid
	if (!(mode == ENCRYPT || mode == DECRYPT))
	  throw openssl_cipher_mode_error();
	free_cipher_context();
	ctx = EVP_CIPHER_CTX_new();
	EVP_CIPHER_CTX_reset (ctx);
	CIPHER_unique_ptr cipher(cipher_type(libctx, alg), EVP_CIPHER_free);

	if (!cipher)
		OPENVPN_THROW(openssl_cipher_error, CryptoAlgs::name(alg) << ": not usable");

	if (!EVP_CipherInit_ex (ctx, cipher.get(), nullptr, key, nullptr, mode))
	  {
	    openssl_clear_error_stack();
	    free_cipher_context();
	    throw openssl_cipher_error("EVP_CipherInit_ex (init)");
	  }
      }

      void reset(const unsigned char *iv)
      {
	check_initialized();
	if (!EVP_CipherInit_ex (ctx, nullptr, nullptr, nullptr, iv, -1))
	  {
	    openssl_clear_error_stack();
	    throw openssl_cipher_error("EVP_CipherInit_ex (reset)");
	  }
      }

      bool update(unsigned char *out, const size_t max_out_size,
		  const unsigned char *in, const size_t in_size,
		  size_t& out_acc)
      {
	check_initialized();
	int outlen;
	if (EVP_CipherUpdate (ctx, out, &outlen, in, int(in_size)))
	  {
	    out_acc += outlen;
	    return true;
	  }
	else
	  {
	    openssl_clear_error_stack();
	    return false;
	  }
      }

      bool final(unsigned char *out, const size_t max_out_size, size_t& out_acc)
      {
	check_initialized();
	int outlen;
	if (EVP_CipherFinal_ex (ctx, out, &outlen))
	  {
	    out_acc += outlen;
	    return true;
	  }
	else
	  {
	    openssl_clear_error_stack();
	    return false;
	  }
      }

      bool is_initialized() const { return ctx != nullptr; }

      size_t iv_length() const
      {
	check_initialized();
	return EVP_CIPHER_CTX_iv_length (ctx);
      }

      size_t block_size() const
      {
	check_initialized();
	return EVP_CIPHER_CTX_block_size (ctx);
      }

      // return cipher mode (such as CIPH_CBC_MODE, etc.)
      int cipher_mode() const
      {
	check_initialized();
	return EVP_CIPHER_CTX_mode (ctx);
      }

    private:
      static evp_cipher_type *cipher_type(SSLLib::Ctx libctx, const CryptoAlgs::Type alg)
      {
	switch (alg)
	  {
	  case CryptoAlgs::AES_128_CBC:
	    return EVP_CIPHER_fetch(libctx, "AES-128-CBC", nullptr);
	  case CryptoAlgs::AES_192_CBC:
	    return EVP_CIPHER_fetch(libctx, "AES-192-CBC", nullptr);
	  case CryptoAlgs::AES_256_CBC:
	    return EVP_CIPHER_fetch(libctx, "AES-256-CBC", nullptr);
	  case CryptoAlgs::AES_256_CTR:
	    return EVP_CIPHER_fetch(libctx, "AES-256-CTR", nullptr);
	  case CryptoAlgs::DES_CBC:
	    return EVP_CIPHER_fetch(libctx, "DES-CBC", nullptr);
	  case CryptoAlgs::DES_EDE3_CBC:
	    return EVP_CIPHER_fetch(libctx, "DES-EDE-CBC", nullptr);
	  case CryptoAlgs::BF_CBC:
	    return EVP_CIPHER_fetch(libctx, "BF-CBC", nullptr);
	  default:
	    return nullptr;
	  }
     }

      void free_cipher_context()
      {
	EVP_CIPHER_CTX_free(ctx);
	ctx = nullptr;
      }

      void check_initialized() const
      {
#ifdef OPENVPN_ENABLE_ASSERT
	if (ctx == nullptr)
	  throw openssl_cipher_uninitialized();
#endif
      }

      EVP_CIPHER_CTX* ctx = nullptr;
    };
  }
}

#endif
