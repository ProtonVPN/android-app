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

// Wrap the OpenSSL HMAC API defined in <openssl/hmac.h> so
// that it can be used as part of the crypto layer of the OpenVPN core.

#pragma once

#include <string>

/* The HMAC_* methods are deprecated in OpenSSL 3.0 and the EVP_MAC methods
 * do no exist in OpenSSL 1.1 yet. So use two distinct implementations */
#if OPENSSL_VERSION_NUMBER < 0x30000000L
#include <openvpn/openssl/crypto/hmac-compat.hpp>
#else

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/openssl/crypto/digest.hpp>

#include <openssl/params.h>


namespace openvpn {
  namespace OpenSSLCrypto {
    class HMACContext
    {
      HMACContext(const HMACContext&) = delete;
      HMACContext& operator=(const HMACContext&) = delete;

    public:
      HMACContext& operator=(HMACContext&& rhs)
      {
	erase();
	ctx = rhs.ctx;
	rhs.ctx = nullptr;
	return *this;
      }

      OPENVPN_SIMPLE_EXCEPTION(openssl_mac_uninitialized);
      OPENVPN_EXCEPTION(openssl_mac_error);

      enum {
        MAX_HMAC_SIZE = EVP_MAX_MD_SIZE
      };

      HMACContext() = default;

      HMACContext(const CryptoAlgs::Type digest, const unsigned char *key, const size_t key_size)
      {
	init(digest, key, key_size);
      }

      ~HMACContext() { erase() ; }

      void init(const CryptoAlgs::Type digest, const unsigned char *key, const size_t key_size)
      {
	erase();
	EVP_MAC* hmac = EVP_MAC_fetch(NULL, "HMAC", NULL);
	ctx = EVP_MAC_CTX_new(hmac);
	EVP_MAC_free(hmac);

	/* Save key since the caller might clear it */
	std::memcpy(this->key, key, key_size);

	/* Lookup/setting of parameters in OpenSSL 3.0 are string based */
	/* The OSSL_PARAM_construct_utf8_string needs a non const str even
	 * though it does not modify the string */
	params[0] = OSSL_PARAM_construct_utf8_string("digest", const_cast<char *>(CryptoAlgs::name(digest)), 0);
	params[1] = OSSL_PARAM_construct_octet_string("key", this->key, key_size);
	params[2] = OSSL_PARAM_construct_end();

	if (!EVP_MAC_init(ctx, NULL, 0, params))
	  {
	    openssl_clear_error_stack();
	    EVP_MAC_CTX_free(ctx);
	    ctx = nullptr;
	    throw openssl_mac_error("EVP_MAC_init (init)");
	  }
      }

      void reset()
      {
        check_initialized();
        if (!EVP_MAC_init (ctx, nullptr, 0, params))
        {
          openssl_clear_error_stack();
          throw openssl_mac_error("EVP_HMAC_Init (reset)");
        }
      }


      void update(const unsigned char *in, const size_t size)
      {
	check_initialized();

	if (!EVP_MAC_update(ctx, in, size))
	  {
	    openssl_clear_error_stack();
	    throw openssl_mac_error("EVP_MAC_Update");
	  }
      }

      /* TODO: This function currently assumes that out has a length of MAX_HMAC_SIZE */
      size_t final(unsigned char *out)
      {
	check_initialized();
	size_t outlen;
	if (!EVP_MAC_final(ctx, out, &outlen, MAX_HMAC_SIZE))
	  {
	    openssl_clear_error_stack();
	    throw openssl_mac_error("HMAC_Final");
	  }
	return outlen;
      }

      size_t size() const
      {
	check_initialized();
	return size_();
      }

      bool is_initialized() const { return ctx != nullptr; }

    private:
      void erase()
      {
	  EVP_MAC_CTX_free(ctx);
	  ctx = nullptr;
      }

      size_t size_() const
      {
        return EVP_MAC_CTX_get_mac_size(ctx);
      }

      void check_initialized() const
      {
#ifdef OPENVPN_ENABLE_ASSERT
	if (!ctx)
	  throw openssl_mac_uninitialized();
#endif
      }

      OSSL_PARAM params[3];
      uint8_t key[EVP_MAX_MD_SIZE];
      EVP_MAC_CTX* ctx = nullptr;
    };
  }
}

#endif