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

// Crypto algorithms

#ifndef OPENVPN_CRYPTO_CRYPTOALGS_H
#define OPENVPN_CRYPTO_CRYPTOALGS_H

#include <string>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/string.hpp>
#include <openvpn/common/likely.hpp>
#include <openvpn/common/arraysize.hpp>

namespace openvpn {
  namespace CryptoAlgs {

    OPENVPN_EXCEPTION(crypto_alg);
    OPENVPN_SIMPLE_EXCEPTION(crypto_alg_index);

    enum class KeyDerivation {
      UNDEFINED,
      OPENVPN_PRF,
      TLS_EKM
    };

    inline const char* name(const KeyDerivation kd)
    {
      switch (kd)
	{
	  case KeyDerivation::UNDEFINED:
	    return "[PRF undefined]";
	  case KeyDerivation::OPENVPN_PRF:
	    return "OpenVPN PRF";
	  case KeyDerivation::TLS_EKM:
	    return "TLS Keying Material Exporter [RFC5705]";
	  default:
	    return "Unknown";
	}
    }

    enum Type {
      NONE=0,

      // CBC ciphers
      AES_128_CBC,
      AES_192_CBC,
      AES_256_CBC,
      DES_CBC,
      DES_EDE3_CBC,
      BF_CBC,

      // CTR ciphers
      AES_256_CTR,

      // AEAD ciphers
      AES_128_GCM,
      AES_192_GCM,
      AES_256_GCM,
      CHACHA20_POLY1305,

      // digests
      MD4,
      MD5,
      SHA1,
      SHA224,
      SHA256,
      SHA384,
      SHA512,

      SIZE,
    };

    enum Mode {
      MODE_UNDEF=0,
      CBC_HMAC,
      AEAD,
      MODE_MASK=0x03,
    };

    enum AlgFlags {       // bits below must start after Mode bits
      F_CIPHER=(1<<2),    // alg is a cipher
      F_DIGEST=(1<<3),    // alg is a digest
      F_ALLOW_DC=(1<<4),  // alg may be used in OpenVPN data channel
      F_NO_CIPHER_DIGEST=(1<<5), // cipher alg does not depend on any additional digest
    };

    // size in bytes of AEAD "nonce tail" normally taken from
    // HMAC key material
    enum {
      AEAD_NONCE_TAIL_SIZE = 8
    };

    class Alg
    {
    public:
      constexpr Alg(const char *name,
	  const unsigned int flags,
	  const unsigned int size,
	  const unsigned int iv_length,
	  const unsigned int block_size)

	: name_(name),
	  flags_(flags),
	  size_(size),
	  iv_length_(iv_length),
	  block_size_(block_size)
      {
      }

      const char *name() const { return name_; }
      unsigned int flags() const { return flags_; }      // contains Mode and AlgFlags
      Mode mode() const { return Mode(flags_ & MODE_MASK); }
      size_t size() const { return size_; }              // digest size
      size_t key_length() const { return size_; }        // cipher key length
      size_t iv_length() const { return iv_length_; }    // cipher only
      size_t block_size() const { return block_size_; }  // cipher only

    private:
      const char *name_;
      unsigned int flags_;
      unsigned int size_;
      unsigned int iv_length_;
      unsigned int block_size_;
    };

    constexpr Alg algs[] = { // NOTE: MUST be indexed by CryptoAlgs::Type (CONST GLOBAL)
      { "NONE",         F_CIPHER|F_DIGEST|F_ALLOW_DC|CBC_HMAC,  0,  0,  0 },
      { "AES-128-CBC",  F_CIPHER|F_ALLOW_DC|CBC_HMAC,          16, 16, 16 },
      { "AES-192-CBC",  F_CIPHER|F_ALLOW_DC|CBC_HMAC,          24, 16, 16 },
      { "AES-256-CBC",  F_CIPHER|F_ALLOW_DC|CBC_HMAC,          32, 16, 16 },
      { "DES-CBC",      F_CIPHER|F_ALLOW_DC|CBC_HMAC,           8,  8,  8 },
      { "DES-EDE3-CBC", F_CIPHER|F_ALLOW_DC|CBC_HMAC,          24,  8,  8 },
      { "BF-CBC",       F_CIPHER|F_ALLOW_DC|CBC_HMAC,          16,  8,  8 },
      { "AES-256-CTR",  F_CIPHER,                              32, 16, 16 },
      { "AES-128-GCM",  F_CIPHER|F_ALLOW_DC|AEAD|F_NO_CIPHER_DIGEST,  16, 12, 16 },
      { "AES-192-GCM",  F_CIPHER|F_ALLOW_DC|AEAD|F_NO_CIPHER_DIGEST,  24, 12, 16 },
      { "AES-256-GCM",  F_CIPHER|F_ALLOW_DC|AEAD|F_NO_CIPHER_DIGEST,  32, 12, 16 },
      { "CHACHA20-POLY1305",  F_CIPHER|F_ALLOW_DC|AEAD|F_NO_CIPHER_DIGEST,  32, 12, 16 },
      { "MD4",          F_DIGEST,                              16,  0,  0 },
      { "MD5",          F_DIGEST|F_ALLOW_DC,                   16,  0,  0 },
      { "SHA1",         F_DIGEST|F_ALLOW_DC,                   20,  0,  0 },
      { "SHA224",       F_DIGEST|F_ALLOW_DC,                   28,  0,  0 },
      { "SHA256",       F_DIGEST|F_ALLOW_DC,                   32,  0,  0 },
      { "SHA384",       F_DIGEST|F_ALLOW_DC,                   48,  0,  0 },
      { "SHA512",       F_DIGEST|F_ALLOW_DC,                   64,  0,  0 },
    };

    inline bool defined(const Type type)
    {
      return type != NONE;
    }

    inline const Alg* get_index_ptr(const size_t i)
    {
      static_assert(SIZE == array_size(algs), "algs array inconsistency");
      if (unlikely(i >= SIZE))
	throw crypto_alg_index();
      return &algs[i];
    }

    inline const Alg& get_index(const size_t i)
    {
      return *get_index_ptr(i);
    }

    inline const Alg* get_ptr(const Type type)
    {
      return get_index_ptr(static_cast<size_t>(type));
    }

    inline const Alg& get(const Type type)
    {
      return get_index(static_cast<size_t>(type));
    }

    inline Type lookup(const std::string& name)
    {
      for (size_t i = 0; i < SIZE; ++i)
	{
	  const Alg& alg = algs[i];
	  if (string::strcasecmp(name, alg.name()) == 0)
	    return static_cast<Type>(i);
	}
      OPENVPN_THROW(crypto_alg, name << ": not found");
    }

    inline const char *name(const Type type)
    {
      return get(type).name();
    }

    inline const char *name(const Type type, const char *default_name)
    {
      if (type == NONE)
	return default_name;
      else
	return get(type).name();
    }

    inline size_t size(const Type type)
    {
      const Alg& alg = get(type);
      return alg.size();
    }

    inline size_t key_length(const Type type)
    {
      const Alg& alg = get(type);
      return alg.key_length();
    }

    inline size_t iv_length(const Type type)
    {
      const Alg& alg = get(type);
      return alg.iv_length();
    }

    inline size_t block_size(const Type type)
    {
      const Alg& alg = get(type);
      return alg.block_size();
    }

    inline Mode mode(const Type type)
    {
      const Alg& alg = get(type);
      return alg.mode();
    }

    inline Type legal_dc_cipher(const Type type)
    {
      const Alg& alg = get(type);
      if ((alg.flags() & (F_CIPHER|F_ALLOW_DC)) != (F_CIPHER|F_ALLOW_DC))
	OPENVPN_THROW(crypto_alg, alg.name() << ": bad cipher for data channel use");
      return type;
    }

    inline Type legal_dc_digest(const Type type)
    {
      const Alg& alg = get(type);
      if ((alg.flags() & (F_DIGEST|F_ALLOW_DC)) != (F_DIGEST|F_ALLOW_DC))
	OPENVPN_THROW(crypto_alg, alg.name() << ": bad digest for data channel use");
      return type;
    }

    /**
     *  Check if a specific algorithm depends on an additional digest or not
     *
     * @param type CryptoAlgs::Type to check
     *
     * @return Returns true if the queried algorithm depends on a digest,
     * 	       otherwise false.  The check is done strictly against the
     * 	       CryptoAlgs::AlgFlags F_NO_CIPHER_DIGEST flag.
     */
    inline bool use_cipher_digest(const Type type)
    {
      const Alg& alg = get(type);
      return !(alg.flags() & F_NO_CIPHER_DIGEST);
    }
  }
}

#endif
