//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2017 OpenVPN Inc.
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

// Wrap the mbed TLS GCM API.

#ifndef OPENVPN_MBEDTLS_CRYPTO_CIPHERGCM_H
#define OPENVPN_MBEDTLS_CRYPTO_CIPHERGCM_H

#include <string>

#include <mbedtls/gcm.h>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/likely.hpp>
#include <openvpn/crypto/static_key.hpp>
#include <openvpn/crypto/cryptoalgs.hpp>

namespace openvpn {
  namespace MbedTLSCrypto {
    class CipherContextGCM
    {
      CipherContextGCM(const CipherContextGCM&) = delete;
      CipherContextGCM& operator=(const CipherContextGCM&) = delete;

    public:
      OPENVPN_EXCEPTION(mbedtls_gcm_error);

      // mode parameter for constructor
      enum {
	MODE_UNDEF = MBEDTLS_OPERATION_NONE,
	ENCRYPT = MBEDTLS_ENCRYPT,
	DECRYPT = MBEDTLS_DECRYPT
      };

      // mbed TLS cipher constants
      enum {
	IV_LEN = 12,
	AUTH_TAG_LEN = 16,
	SUPPORTS_IN_PLACE_ENCRYPT = 1,
      };

#if 0
      // mbed TLS encrypt/decrypt return values
      enum {
	GCM_AUTH_FAILED = MBEDTLS_ERR_GCM_AUTH_FAILED,
	SUCCESS = 0,
      };
#endif

      CipherContextGCM()
	: initialized(false)
      {
      }

      ~CipherContextGCM() { erase() ; }

      void init(const CryptoAlgs::Type alg,
		const unsigned char *key,
		const unsigned int keysize,
		const int mode) // unused
      {
	erase();

	// get cipher type
	unsigned int ckeysz = 0;
	const mbedtls_cipher_id_t cid = cipher_type(alg, ckeysz);
	if (ckeysz > keysize)
	  throw mbedtls_gcm_error("insufficient key material");

	// initialize cipher context
	mbedtls_gcm_init(&ctx);
	if (mbedtls_gcm_setkey(&ctx, cid, key, ckeysz * 8) < 0)
	    throw mbedtls_gcm_error("mbedtls_gcm_setkey");

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
	const int status = mbedtls_gcm_crypt_and_tag(&ctx, MBEDTLS_GCM_ENCRYPT,
						     length, iv, IV_LEN, ad, ad_len,
						     input, output, AUTH_TAG_LEN, tag);
	if (unlikely(status))
	  OPENVPN_THROW(mbedtls_gcm_error, "mbedtls_gcm_crypt_and_tag failed with status=" << status);
      }

      // input and output may NOT be equal
      bool decrypt(const unsigned char *input,
		  unsigned char *output,
		  size_t length,
		  const unsigned char *iv,
		  const unsigned char *tag,
		  const unsigned char *ad,
		  size_t ad_len)
      {
	check_initialized();
	const int status = mbedtls_gcm_auth_decrypt(&ctx, length, iv, IV_LEN, ad, ad_len, tag,
						    AUTH_TAG_LEN, input, output);
	return status == 0;
      }

      bool is_initialized() const { return initialized; }

    private:
      static mbedtls_cipher_id_t cipher_type(const CryptoAlgs::Type alg, unsigned int& keysize)
      {
	switch (alg)
	  {
	  case CryptoAlgs::AES_128_GCM:
	    keysize = 16;
	    return MBEDTLS_CIPHER_ID_AES;
	  case CryptoAlgs::AES_192_GCM:
	    keysize = 24;
	    return MBEDTLS_CIPHER_ID_AES;
	  case CryptoAlgs::AES_256_GCM:
	    keysize = 32;
	    return MBEDTLS_CIPHER_ID_AES;
	  default:
	    OPENVPN_THROW(mbedtls_gcm_error, CryptoAlgs::name(alg) << ": not usable");
	  }
      }

      void erase()
      {
	if (initialized)
	  {
	    mbedtls_gcm_free(&ctx);
	    initialized = false;
	  }
      }

      void check_initialized() const
      {
	if (unlikely(!initialized))
	  throw mbedtls_gcm_error("uninitialized");
      }

      bool initialized;
      mbedtls_gcm_context ctx;
    };
  }
}

#endif
