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

// Wrap the Apple cipher API defined in <CommonCrypto/CommonCryptor.h> so
// that it can be used as part of the crypto layer of the OpenVPN core.

#ifndef OPENVPN_APPLECRYPTO_CRYPTO_CIPHER_H
#define OPENVPN_APPLECRYPTO_CRYPTO_CIPHER_H

#include <string>
#include <cstring>

#include <CommonCrypto/CommonCryptor.h>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/platform.hpp>
#include <openvpn/common/string.hpp>
#include <openvpn/crypto/static_key.hpp>
#include <openvpn/crypto/cryptoalgs.hpp>
#include <openvpn/apple/cf/error.hpp>

namespace openvpn {
  namespace AppleCrypto {
    class CipherContext
    {
      CipherContext(const CipherContext&) = delete;
      CipherContext& operator=(const CipherContext&) = delete;

    public:
      OPENVPN_SIMPLE_EXCEPTION(apple_cipher_mode_error);
      OPENVPN_SIMPLE_EXCEPTION(apple_cipher_uninitialized);
      OPENVPN_EXCEPTION(apple_cipher_error);

      // mode parameter for constructor
      enum {
	MODE_UNDEF = -1,
	ENCRYPT = kCCEncrypt,
	DECRYPT = kCCDecrypt
      };

      enum {
	MAX_IV_LENGTH = 16,
	CIPH_CBC_MODE = 0
      };

      CipherContext()
	: cinfo(nullptr), cref(nullptr)
      {
      }

      ~CipherContext() { erase() ; }

      void init(const CryptoAlgs::Type alg, const unsigned char *key, const int mode)
      {
	erase();

	// check that mode is valid
	if (!(mode == ENCRYPT || mode == DECRYPT))
	  throw apple_cipher_mode_error();

	// initialize cipher context with cipher type
	const CCCryptorStatus status = CCCryptorCreate(mode,
						       cipher_type(alg),
						       kCCOptionPKCS7Padding,
						       key,
						       CryptoAlgs::key_length(alg),
						       nullptr,
						       &cref);
	if (status != kCCSuccess)
	  throw CFException("CipherContext: CCCryptorCreate", status);

	cinfo = CryptoAlgs::get_ptr(alg);
      }

      void reset(const unsigned char *iv)
      {
	check_initialized();
	const CCCryptorStatus status = CCCryptorReset(cref, iv);
	if (status != kCCSuccess)
	  throw CFException("CipherContext: CCCryptorReset", status);
      }

      bool update(unsigned char *out, const size_t max_out_size,
		  const unsigned char *in, const size_t in_size,
		  size_t& out_acc)
      {
	check_initialized();
	size_t dataOutMoved;
	const CCCryptorStatus status = CCCryptorUpdate(cref, in, in_size, out, max_out_size, &dataOutMoved);
	if (status == kCCSuccess)
	  {
	    out_acc += dataOutMoved;
	    return true;
	  }
	else
	  return false;
      }

      bool final(unsigned char *out, const size_t max_out_size, size_t& out_acc)
      {
	check_initialized();
	size_t dataOutMoved;
	const CCCryptorStatus status = CCCryptorFinal(cref, out, max_out_size, &dataOutMoved);
	if (status == kCCSuccess)
	  {
	    out_acc += dataOutMoved;
	    return true;
	  }
	else
	  return false;
      }

      bool is_initialized() const { return cinfo != nullptr; }

      size_t iv_length() const
      {
	check_initialized();
	return cinfo->iv_length();
      }

      size_t block_size() const
      {
	check_initialized();
	return cinfo->block_size();
      }

      // return cipher mode (such as CIPH_CBC_MODE, etc.)
      int cipher_mode() const
      {
	check_initialized();
	return CIPH_CBC_MODE;
      }

    private:
      static CCAlgorithm cipher_type(const CryptoAlgs::Type alg)
      {
	switch (alg)
	  {
	  case CryptoAlgs::AES_128_CBC:
	  case CryptoAlgs::AES_192_CBC:
	  case CryptoAlgs::AES_256_CBC:
	  case CryptoAlgs::AES_256_CTR:
	    return kCCAlgorithmAES128;
	  case CryptoAlgs::DES_CBC:
	    return kCCAlgorithmDES;
	  case CryptoAlgs::DES_EDE3_CBC:
	    return kCCAlgorithm3DES;
#ifdef OPENVPN_PLATFORM_IPHONE
	  case CryptoAlgs::BF_CBC:
	    return kCCAlgorithmBlowfish;
#endif
	  default:
	    OPENVPN_THROW(apple_cipher_error, CryptoAlgs::name(alg) << ": not usable");
	  }
      }

      void erase()
      {
	if (cinfo)
	  {
	    if (cref)
	      CCCryptorRelease(cref);
	    cref = nullptr;
	    cinfo = nullptr;
	  }
      }

      void check_initialized() const
      {
#ifdef OPENVPN_ENABLE_ASSERT
	if (!cinfo)
	  throw apple_cipher_uninitialized();
#endif
      }

      const CryptoAlgs::Alg* cinfo;
      CCCryptorRef cref;
    };
  }
}

#endif
