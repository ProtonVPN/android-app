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

#ifndef OPENVPN_APPLECRYPTO_CRYPTO_HMAC_H
#define OPENVPN_APPLECRYPTO_CRYPTO_HMAC_H

// Wrap the Apple HMAC API defined in <CommonCrypto/CommonHMAC.h> so that
// it can be used as part of the crypto layer of the OpenVPN core.

#include <string>
#include <cstring>

#include <CommonCrypto/CommonHMAC.h>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/applecrypto/crypto/digest.hpp>

namespace openvpn {
  namespace AppleCrypto {
    class HMACContext
    {
      HMACContext(const HMACContext&) = delete;
      HMACContext& operator=(const HMACContext&) = delete;

    public:
      OPENVPN_EXCEPTION(digest_cannot_be_used_with_hmac);
      OPENVPN_SIMPLE_EXCEPTION(hmac_uninitialized);
      OPENVPN_SIMPLE_EXCEPTION(hmac_keysize_error);

      enum {
	MAX_HMAC_SIZE = DigestContext::MAX_DIGEST_SIZE,
	MAX_HMAC_KEY_SIZE = 128,
      };

      HMACContext()
      {
	state = PRE;
      }

      HMACContext(const CryptoAlgs::Type digest, const unsigned char *key, const size_t key_size)
      {
	init(digest, key, key_size);
      }

      ~HMACContext()
      {
      }

      void init(const CryptoAlgs::Type digest, const unsigned char *key, const size_t key_size)
      {
	state = PRE;
	info = DigestContext::digest_type(digest);
	digest_size_ = CryptoAlgs::size(digest);
	hmac_alg = info->hmac_alg();
	if (hmac_alg == DigestInfo::NO_HMAC_ALG)
	  throw digest_cannot_be_used_with_hmac(info->name());
	if (key_size > MAX_HMAC_KEY_SIZE)
	  throw hmac_keysize_error();
	std::memcpy(key_, key, key_size_ = key_size);
	state = PARTIAL;
      }

      void reset() // Apple HMAC API is missing reset method, so we have to reinit
      {
	cond_reset(true);
      }

      void update(const unsigned char *in, const size_t size)
      {
	cond_reset(false);
	CCHmacUpdate(&ctx, in, size);
      }

      size_t final(unsigned char *out)
      {
	cond_reset(false);
	CCHmacFinal(&ctx, out);
	return digest_size_;
      }

      size_t size() const
      {
	if (!is_initialized())
	  throw hmac_uninitialized();
	return digest_size_;
      }

      bool is_initialized() const
      {
	return state >= PARTIAL;
      }

    private:
      void cond_reset(const bool force_init)
      {
	switch (state)
	  {
	  case PRE:
	    throw hmac_uninitialized();
	  case READY:
	    if (!force_init)
	      return;
	  case PARTIAL:
	    CCHmacInit(&ctx, hmac_alg, key_, key_size_);
	    state = READY;
	  }
      }

      enum State {
	PRE=0,
	PARTIAL,
	READY
      };
      int state;

      const DigestInfo *info;
      CCHmacAlgorithm hmac_alg;
      size_t key_size_;
      size_t digest_size_;
      unsigned char key_[MAX_HMAC_KEY_SIZE];
      CCHmacContext ctx;
    };
  }
}

#endif
