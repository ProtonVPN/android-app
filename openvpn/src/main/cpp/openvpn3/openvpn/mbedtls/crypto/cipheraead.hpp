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

// Wrap the mbed TLS AEAD API.

#pragma once

#include <string>

#include <mbedtls/gcm.h>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/likely.hpp>
#include <openvpn/crypto/static_key.hpp>
#include <openvpn/crypto/cryptoalgs.hpp>
#include <openvpn/mbedtls/crypto/cipher.hpp>

namespace openvpn {
  namespace MbedTLSCrypto {
    class CipherContextAEAD : public CipherContextCommon
    {
      CipherContextAEAD(const CipherContextAEAD&) = delete;
      CipherContextAEAD& operator=(const CipherContextAEAD&) = delete;

    public:
      OPENVPN_EXCEPTION(mbedtls_aead_error);


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

      CipherContextAEAD() = default;

      ~CipherContextAEAD() { erase() ; }

      void init(SSLLib::Ctx libctx, const CryptoAlgs::Type alg,
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

	auto* ci = mbedtls_cipher_info_from_type(cid);

	// initialize cipher context with cipher type
	if (mbedtls_cipher_setup(&ctx, ci) < 0)
	  throw mbedtls_aead_error("mbedtls_cipher_setup");

	if (mbedtls_cipher_setkey(&ctx, key, ckeysz * 8, (mbedtls_operation_t)mode) < 0)
	    throw mbedtls_aead_error("mbedtls_cipher_setkey");

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
	const int status = mbedtls_cipher_auth_encrypt(&ctx, iv, IV_LEN, ad, ad_len, input, length,
						       output, &length, tag, AUTH_TAG_LEN);
	if (unlikely(status))
	  OPENVPN_THROW(mbedtls_aead_error, "mbedtls_gcm_crypt_and_tag failed with status=" << status);
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

	// Older versions of mbed TLS have the tag a non const, even though it is
	// not modified, const cast it here
	const int status = mbedtls_cipher_auth_encrypt(&ctx, iv, IV_LEN, ad, ad_len, input, length, output, &length,
						       const_cast<unsigned char*>(tag), AUTH_TAG_LEN);
	return status == 0;
      }

      bool is_initialized() const { return initialized; }

      static bool is_supported(void *libctx, const CryptoAlgs::Type alg)
      {
        unsigned int keysize;
       	return (cipher_type(alg, keysize) != MBEDTLS_CIPHER_NONE);
      }

    private:
      static mbedtls_cipher_type_t cipher_type(const CryptoAlgs::Type alg, unsigned int& keysize)
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
	    keysize =0;
	    return MBEDTLS_CIPHER_NONE;
	  }
      }
    };
  }
}
