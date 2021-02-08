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

// Wrap the OpenSSL digest API defined in <openssl/evp.h>
// so that it can be used as part of the crypto layer of the OpenVPN core.

#ifndef OPENVPN_OPENSSL_CRYPTO_DIGEST_H
#define OPENVPN_OPENSSL_CRYPTO_DIGEST_H

#include <string>

#include <openssl/objects.h>
#include <openssl/evp.h>
#include <openssl/md4.h>
#include <openssl/md5.h>
#include <openssl/sha.h>
#include <openssl/hmac.h>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/crypto/cryptoalgs.hpp>
#include <openvpn/openssl/util/error.hpp>

#include <openvpn/openssl/compat.hpp>

namespace openvpn {
  namespace OpenSSLCrypto {
    class HMACContext;

    class DigestContext
    {
      DigestContext(const DigestContext&) = delete;
      DigestContext& operator=(const DigestContext&) = delete;

    public:
      friend class HMACContext;

      OPENVPN_SIMPLE_EXCEPTION(openssl_digest_uninitialized);
      OPENVPN_EXCEPTION(openssl_digest_error);

      enum {
	MAX_DIGEST_SIZE = EVP_MAX_MD_SIZE
      };

      DigestContext()
	: initialized(false)
      {
      }

      DigestContext(const CryptoAlgs::Type alg)
	: initialized(false)
      {
	init(alg);
      }

      ~DigestContext() { erase() ; }

      void init(const CryptoAlgs::Type alg)
      {
	erase();
	ctx=EVP_MD_CTX_new ();
	if (!EVP_DigestInit(ctx, digest_type(alg)))
	  {
	    openssl_clear_error_stack();
	    throw openssl_digest_error("EVP_DigestInit");
	  }
	initialized = true;
      }

      void update(const unsigned char *in, const size_t size)
      {
	check_initialized();
	if (!EVP_DigestUpdate(ctx, in, int(size)))
	  {
	    openssl_clear_error_stack();
	    throw openssl_digest_error("EVP_DigestUpdate");
	  }
      }

      size_t final(unsigned char *out)
      {
	check_initialized();
	unsigned int outlen;
	if (!EVP_DigestFinal(ctx, out, &outlen))
	  {
	    openssl_clear_error_stack();
	    throw openssl_digest_error("EVP_DigestFinal");
	  }
	return outlen;
      }

      size_t size() const
      {
	check_initialized();
	return EVP_MD_CTX_size(ctx);
      }

      bool is_initialized() const { return initialized; }

    private:
      static const EVP_MD *digest_type(const CryptoAlgs::Type alg)
      {
	switch (alg)
	  {
	  case CryptoAlgs::MD4:
	    return EVP_md4();
	  case CryptoAlgs::MD5:
	    return EVP_md5();
	  case CryptoAlgs::SHA1:
	    return EVP_sha1();
	  case CryptoAlgs::SHA224:
	    return EVP_sha224();
	  case CryptoAlgs::SHA256:
	    return EVP_sha256();
	  case CryptoAlgs::SHA384:
	    return EVP_sha384();
	  case CryptoAlgs::SHA512:
	    return EVP_sha512();
	  default:
	    OPENVPN_THROW(openssl_digest_error, CryptoAlgs::name(alg) << ": not usable");
	  }
      }

      void erase()
      {
	if (initialized)
	  {
#if OPENSSL_VERSION_NUMBER < 0x10100000L
	    EVP_MD_CTX_cleanup(ctx);
#endif
	    EVP_MD_CTX_free(ctx);
	    initialized = false;
	  }
      }

      void check_initialized() const
      {
#ifdef OPENVPN_ENABLE_ASSERT
	if (!initialized)
	  throw openssl_digest_uninitialized();
#endif
      }

      bool initialized;
      EVP_MD_CTX *ctx;
    };
  }
}

#endif
