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

// Wrap the Apple digest API defined in <CommonCrypto/CommonDigest.h>
// so that it can be used as part of the crypto layer of the OpenVPN core.

#ifndef OPENVPN_APPLECRYPTO_CRYPTO_DIGEST_H
#define OPENVPN_APPLECRYPTO_CRYPTO_DIGEST_H

#include <string>

#include <CommonCrypto/CommonDigest.h>
#include <CommonCrypto/CommonHMAC.h>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/string.hpp>
#include <openvpn/crypto/cryptoalgs.hpp>
#include <openvpn/apple/cf/error.hpp>

#define OPENVPN_DIGEST_CONTEXT(TYPE) CC_##TYPE##_CTX TYPE##_ctx

#define OPENVPN_DIGEST_ALG_CLASS(TYPE) \
  class DigestAlgorithm##TYPE : public DigestAlgorithm \
  { \
  public: \
    DigestAlgorithm##TYPE() {} \
    virtual int init(DigestCTX& ctx) const \
    { \
      return CC_##TYPE##_Init(&ctx.u.TYPE##_ctx); \
    } \
    virtual int update(DigestCTX& ctx, const unsigned char *data, size_t size) const \
    { \
      return CC_##TYPE##_Update(&ctx.u.TYPE##_ctx, data, size); \
    } \
    virtual int final(DigestCTX& ctx, unsigned char *md) const \
    { \
      return CC_##TYPE##_Final(md, &ctx.u.TYPE##_ctx); \
    } \
  }

#define OPENVPN_DIGEST_ALG_DECLARE(TYPE) const DigestAlgorithm##TYPE alg_##TYPE;

#define OPENVPN_DIGEST_INFO_DECLARE(TYPE) const DigestInfo info_##TYPE(CryptoAlgs::TYPE, &alg_##TYPE, kCCHmacAlg##TYPE)

#define OPENVPN_DIGEST_INFO_DECLARE_NO_HMAC(TYPE) const DigestInfo info_##TYPE(CryptoAlgs::TYPE, &alg_##TYPE, DigestInfo::NO_HMAC_ALG)

namespace openvpn {
  namespace AppleCrypto {
    typedef CC_SHA256_CTX CC_SHA224_CTX;
    typedef CC_SHA512_CTX CC_SHA384_CTX;

    struct DigestCTX {
      union {
	OPENVPN_DIGEST_CONTEXT(MD4);
	OPENVPN_DIGEST_CONTEXT(MD5);
	OPENVPN_DIGEST_CONTEXT(SHA1);
	OPENVPN_DIGEST_CONTEXT(SHA224);
	OPENVPN_DIGEST_CONTEXT(SHA256);
	OPENVPN_DIGEST_CONTEXT(SHA384);
	OPENVPN_DIGEST_CONTEXT(SHA512);
      } u;
    };

    struct DigestAlgorithm {
      virtual int init(DigestCTX& ctx) const = 0;
      virtual int update(DigestCTX& ctx, const unsigned char *data, size_t size) const = 0;
      virtual int final(DigestCTX& ctx, unsigned char *md) const = 0;
    };

    // individual digest algorithm classes (each inherits from DigestAlgorithm)
    OPENVPN_DIGEST_ALG_CLASS(MD4);
    OPENVPN_DIGEST_ALG_CLASS(MD5);
    OPENVPN_DIGEST_ALG_CLASS(SHA1);
    OPENVPN_DIGEST_ALG_CLASS(SHA224);
    OPENVPN_DIGEST_ALG_CLASS(SHA256);
    OPENVPN_DIGEST_ALG_CLASS(SHA384);
    OPENVPN_DIGEST_ALG_CLASS(SHA512);

    class DigestInfo
    {
    public:
      enum {
	NO_HMAC_ALG = -1
      };

      DigestInfo(CryptoAlgs::Type type,
		 const DigestAlgorithm* digest_alg,
		 const CCHmacAlgorithm hmac_alg)
	: type_(type),
	  digest_alg_(digest_alg),
	  hmac_alg_(hmac_alg) {}

      CryptoAlgs::Type type() const { return type_; }
      const char *name() const { return CryptoAlgs::name(type_); }
      size_t size() const { return CryptoAlgs::size(type_); }
      const DigestAlgorithm* digest_alg() const { return digest_alg_; }
      CCHmacAlgorithm hmac_alg() const { return hmac_alg_; }

    private:
      CryptoAlgs::Type type_;
      const DigestAlgorithm* digest_alg_;
      CCHmacAlgorithm hmac_alg_;
    };

    // instantiate individual digest algorithm class instances (each inherits from DigestAlgorithm),
    // naming convention is alg_TYPE
    OPENVPN_DIGEST_ALG_DECLARE(MD4);
    OPENVPN_DIGEST_ALG_DECLARE(MD5);
    OPENVPN_DIGEST_ALG_DECLARE(SHA1);
    OPENVPN_DIGEST_ALG_DECLARE(SHA224);
    OPENVPN_DIGEST_ALG_DECLARE(SHA256);
    OPENVPN_DIGEST_ALG_DECLARE(SHA384);
    OPENVPN_DIGEST_ALG_DECLARE(SHA512);

    // instantiate individual digest info class instances (each is a DigestInfo),
    // naming convention is info_TYPE
    OPENVPN_DIGEST_INFO_DECLARE_NO_HMAC(MD4);
    OPENVPN_DIGEST_INFO_DECLARE(MD5);
    OPENVPN_DIGEST_INFO_DECLARE(SHA1);
    OPENVPN_DIGEST_INFO_DECLARE(SHA224);
    OPENVPN_DIGEST_INFO_DECLARE(SHA256);
    OPENVPN_DIGEST_INFO_DECLARE(SHA384);
    OPENVPN_DIGEST_INFO_DECLARE(SHA512);

    class HMACContext;

    class DigestContext
    {
      DigestContext(const DigestContext&) = delete;
      DigestContext& operator=(const DigestContext&) = delete;

    public:
      friend class HMACContext;

      OPENVPN_SIMPLE_EXCEPTION(apple_digest_uninitialized);
      OPENVPN_SIMPLE_EXCEPTION(apple_digest_final_overflow);
      OPENVPN_EXCEPTION(apple_digest_error);

      enum {
	MAX_DIGEST_SIZE = CC_SHA512_DIGEST_LENGTH // largest known is SHA512
      };

      DigestContext()
      {
	clear();
      }

      DigestContext(const CryptoAlgs::Type alg)
      {
	init(alg);
      }

      void init(const CryptoAlgs::Type alg)
      {
	clear();
	info = digest_type(alg);
	meth = info->digest_alg();
	if (meth->init(ctx) != 1)
	  throw apple_digest_error("init");
	initialized = true;
      }

      void update(const unsigned char *in, const size_t size)
      {
	check_initialized();
	if (meth->update(ctx, in, size) != 1)
	  throw apple_digest_error("update");
      }

      size_t final(unsigned char *out)
      {
	check_initialized();
	if (meth->final(ctx, out) != 1)
	  throw apple_digest_error("final");
	return info->size();
      }

      size_t size() const
      {
	check_initialized();
	return info->size();
      }

      bool is_initialized() const { return initialized; }

    private:
      static const DigestInfo *digest_type(const CryptoAlgs::Type alg)
      {
	switch (alg)
	  {
	  case CryptoAlgs::MD4:
	    return &info_MD4;
	  case CryptoAlgs::MD5:
	    return &info_MD5;
	  case CryptoAlgs::SHA1:
	    return &info_SHA1;
	  case CryptoAlgs::SHA224:
	    return &info_SHA224;
	  case CryptoAlgs::SHA256:
	    return &info_SHA256;
	  case CryptoAlgs::SHA384:
	    return &info_SHA384;
	  case CryptoAlgs::SHA512:
	    return &info_SHA512;
	  default:
	    OPENVPN_THROW(apple_digest_error, CryptoAlgs::name(alg) << ": not usable");
	  }
      }

      void clear()
      {
	initialized = false;
      }

      void check_initialized() const
      {
#ifdef OPENVPN_ENABLE_ASSERT
	if (!initialized)
	  throw apple_digest_uninitialized();
#endif
      }

      bool initialized;
      const DigestInfo *info;
      const DigestAlgorithm *meth;
      DigestCTX ctx;
    };
  }
}

#undef OPENVPN_DIGEST_CONTEXT
#undef OPENVPN_DIGEST_ALG_CLASS
#undef OPENVPN_DIGEST_ALG_DECLARE
#undef OPENVPN_DIGEST_INFO_DECLARE

#endif
