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

// Wrap the OpenSSL GCM API.

#pragma once

#include <string>

#include <openssl/objects.h>
#include <openssl/evp.h>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/likely.hpp>
#include <openvpn/crypto/static_key.hpp>
#include <openvpn/crypto/cryptoalgs.hpp>
#include <openvpn/openssl/util/error.hpp>

namespace openvpn {
  namespace OpenSSLCrypto {
    class CipherContextAEAD
    {
      /* In OpenSSL 3.0 the method that returns EVP_CIPHER, the cipher needs to be
 * freed afterwards, thus needing a non-const type. In contrast, OpenSSL 1.1.1
 * and lower returns a const type, needing a const type */
#if OPENSSL_VERSION_NUMBER < 0x30000000L
    using evp_cipher_type = const EVP_CIPHER;
#else
    using evp_cipher_type = EVP_CIPHER;
#endif
      using CIPHER_unique_ptr = std::unique_ptr<evp_cipher_type, decltype(&::EVP_CIPHER_free)>;

    public:
      CipherContextAEAD(const CipherContextAEAD&) = delete;
      CipherContextAEAD& operator=(const CipherContextAEAD&) = delete;

      OPENVPN_EXCEPTION(openssl_gcm_error);

      // mode parameter for constructor
      enum {
	MODE_UNDEF = -1,
	ENCRYPT = 1,
	DECRYPT = 0
      };

      // OpenSSL cipher constants
      enum {
	IV_LEN = 12,
	AUTH_TAG_LEN = 16,
	SUPPORTS_IN_PLACE_ENCRYPT = 0,
      };

      CipherContextAEAD() = default;

      ~CipherContextAEAD() { free_cipher_context(); }

      void init(SSLLib::Ctx libctx,
		  const CryptoAlgs::Type alg,
		const unsigned char *key,
		const unsigned int keysize,
		const int mode)
      {
	free_cipher_context();
	unsigned int ckeysz = 0;
	CIPHER_unique_ptr ciph(cipher_type(libctx, alg, ckeysz), EVP_CIPHER_free);

	if (!ciph)
	  OPENVPN_THROW(openssl_gcm_error, CryptoAlgs::name(alg) << ": not usable");

	if (ckeysz > keysize)
	  throw openssl_gcm_error("insufficient key material");
	ctx = EVP_CIPHER_CTX_new();
	EVP_CIPHER_CTX_reset(ctx);
	switch (mode)
	  {
	  case ENCRYPT:
	    if (!EVP_EncryptInit_ex(ctx, ciph.get(), nullptr, key, nullptr))
	      {
		openssl_clear_error_stack();
		free_cipher_context();
		throw openssl_gcm_error("EVP_EncryptInit_ex (init)");
	      }
	    break;
	  case DECRYPT:
	    if (!EVP_DecryptInit_ex(ctx, ciph.get(), nullptr, key, nullptr))
	      {
		openssl_clear_error_stack();
		free_cipher_context();
		throw openssl_gcm_error("EVP_DecryptInit_ex (init)");
	      }
	    break;
	  default:
	    throw openssl_gcm_error("bad mode");
	  }
	if (EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_IVLEN, IV_LEN, nullptr) != 1)
	  {
	    openssl_clear_error_stack();
	    free_cipher_context();
	    throw openssl_gcm_error("EVP_CIPHER_CTX_ctrl set IV len");
	  }
      }

      void encrypt(const unsigned char *input,
		   unsigned char *output,
		   size_t length,
		   const unsigned char *iv,
		   unsigned char *tag,
		   const unsigned char *ad,
		   size_t ad_len)
      {
	int len;
	int ciphertext_len;

	check_initialized();
	if (!EVP_EncryptInit_ex(ctx, nullptr, nullptr, nullptr, iv))
	  {
	    openssl_clear_error_stack();
	    throw openssl_gcm_error("EVP_EncryptInit_ex (reset)");
	  }
	if (!EVP_EncryptUpdate(ctx, nullptr, &len, ad, int(ad_len)))
	  {
	    openssl_clear_error_stack();
	    throw openssl_gcm_error("EVP_EncryptUpdate AD");
	  }
	if (!EVP_EncryptUpdate(ctx, output, &len, input, int(length)))
	  {
	    openssl_clear_error_stack();
	    throw openssl_gcm_error("EVP_EncryptUpdate data");
	  }
	ciphertext_len = len;
	if (!EVP_EncryptFinal_ex(ctx, output+len, &len))
	  {
	    openssl_clear_error_stack();
	    throw openssl_gcm_error("EVP_EncryptFinal_ex");
	  }
	ciphertext_len += len;
	if ((size_t) ciphertext_len != length)
	  {
	    throw openssl_gcm_error("encrypt size inconsistency");
	  }
	if (!EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_GET_TAG, AUTH_TAG_LEN, tag))
	  {
	    openssl_clear_error_stack();
	    throw openssl_gcm_error("EVP_CIPHER_CTX_ctrl get tag");
	  }
      }

      bool decrypt(const unsigned char *input,
		  unsigned char *output,
		  size_t length,
		  const unsigned char *iv,
		  unsigned char *tag,
		  const unsigned char *ad,
		  size_t ad_len)
      {
	int len;
	int plaintext_len;

	check_initialized();
	if (!EVP_DecryptInit_ex(ctx, nullptr, nullptr, nullptr, iv))
	  {
	    openssl_clear_error_stack();
	    throw openssl_gcm_error("EVP_DecryptInit_ex (reset)");
	  }
	if (!EVP_DecryptUpdate(ctx, nullptr, &len, ad, int(ad_len)))
	  {
	    openssl_clear_error_stack();
	    throw openssl_gcm_error("EVP_DecryptUpdate AD");
	  }
	if (!EVP_DecryptUpdate(ctx, output, &len, input, int(length)))
	  {
	    openssl_clear_error_stack();
	    throw openssl_gcm_error("EVP_DecryptUpdate data");
	  }
	plaintext_len = len;
	if (!EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_TAG, AUTH_TAG_LEN, tag))
	  {
	    openssl_clear_error_stack();
	    throw openssl_gcm_error("EVP_CIPHER_CTX_ctrl set tag");
	  }
	if (!EVP_DecryptFinal_ex(ctx, output+len, &len))
	  {
	    openssl_clear_error_stack();
	    return false;
	  }
	plaintext_len += len;
	if (static_cast<size_t>(plaintext_len) != length)
	  {
	    throw openssl_gcm_error("decrypt size inconsistency");
	  }
	return true;
      }

      bool is_initialized() const { return ctx != nullptr; }

      static bool is_supported(SSLLib::Ctx libctx, const CryptoAlgs::Type alg)
      {
	unsigned int keysize = 0;
	CIPHER_unique_ptr cipher(cipher_type(libctx, alg, keysize), EVP_CIPHER_free);
	return (bool)cipher;
      }


    private:
      static evp_cipher_type *cipher_type(SSLLib::Ctx libctx, const CryptoAlgs::Type alg,
					   unsigned int& keysize)
      {
	switch (alg)
	{
#if OPENSSL_VERSION_NUMBER < 0x10100000L
	case CryptoAlgs::AES_128_GCM:
	  keysize = 16;
	  return EVP_CIPHER_fetch(libctx, "id-aes128-GCM", nullptr);
	case CryptoAlgs::AES_192_GCM:
	  keysize = 24;
	  return EVP_CIPHER_fetch(libctx, "id-aes192-GCM", nullptr);
	case CryptoAlgs::AES_256_GCM:
	  keysize = 32;
	  return EVP_CIPHER_fetch(libctx, "id-aes256-GCM", nullptr);
#else
	  case CryptoAlgs::AES_128_GCM:
	    keysize = 16;
	    return EVP_CIPHER_fetch(libctx, "AES-128-GCM", nullptr);
	  case CryptoAlgs::AES_192_GCM:
	    keysize = 24;
	    return EVP_CIPHER_fetch(libctx, "AES-192-GCM", nullptr);
	  case CryptoAlgs::AES_256_GCM:
	    keysize = 32;
	    return EVP_CIPHER_fetch(libctx, "AES-256-GCM", nullptr);
#endif
	  case CryptoAlgs::CHACHA20_POLY1305:
	      keysize = 32;
	      return EVP_CIPHER_fetch(libctx, "CHACHA20-POLY1305", nullptr);
	  default:
	       keysize = 0;
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
	if (!ctx)
	  throw openssl_gcm_error("uninitialized");
#endif
      }

      EVP_CIPHER_CTX *ctx = nullptr;
    };
  }
}

