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

#pragma once

#include <cstring>
#include <memory>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/hash.hpp>
#include <openvpn/common/base64.hpp>
#include <openvpn/common/string.hpp>
#include <openvpn/buffer/buffer.hpp>
#include <openvpn/random/randapi.hpp>

namespace openvpn {

  class OpenSSLContext;
  class MbedTLSContext;

  // Abstract base class used to provide an interface for TLS
  // Session Ticket keying originally described by RFC 5077.
  class TLSSessionTicketBase
  {
  public:
    typedef std::unique_ptr<TLSSessionTicketBase> UPtr;

    OPENVPN_EXCEPTION(sess_ticket_error);

    enum Status {
      NO_TICKET,
      TICKET_AVAILABLE,
      TICKET_EXPIRING,
    };

    class Name
    {
    public:
      static constexpr size_t SIZE = 16;

      explicit Name(RandomAPI& rng)
      {
	rng.rand_bytes(value_, SIZE);
      }

      explicit Name(const std::string& name_b64)
      {
	b64_to_key(name_b64, "key name", value_, SIZE);
      }

      explicit Name(const unsigned char name[SIZE])
      {
	std::memcpy(value_, name, SIZE);
      }

      bool operator==(const Name& rhs) const
      {
	return std::memcmp(value_, rhs.value_, SIZE) == 0;
      }

      bool operator!=(const Name& rhs) const
      {
	return std::memcmp(value_, rhs.value_, SIZE) != 0;
      }

      bool operator<(const Name& rhs) const
      {
	return std::memcmp(value_, rhs.value_, SIZE) < 0;
      }

      std::string to_string() const
      {
	return "TLSTicketName[" + b64() + ']';
      }

      std::string b64() const
      {
	return base64->encode(value_, SIZE);
      }

      template <typename HASH>
      void hash(HASH& h) const
      {
	h(value_, SIZE);
      }

#ifdef USE_OPENVPN_HASH
      std::size_t hashval() const
      {
	Hash64 h;
	hash(h);
	return h.value();
      }
#endif

    private:
      // we need to friend SSL implementation classes
      friend class OpenSSLContext;
      friend class MbedTLSContext;

      Name() {} // note that default constructor leaves object in an undefined state

      unsigned char value_[SIZE];
    };

    class Key
    {
    public:
      static constexpr size_t CIPHER_KEY_SIZE = 32;
      static constexpr size_t HMAC_KEY_SIZE = 16;

      explicit Key(RandomAPI& rng)
      {
	rng.assert_crypto();
	rng.rand_bytes(cipher_value_, CIPHER_KEY_SIZE);
	rng.rand_bytes(hmac_value_, HMAC_KEY_SIZE);
      }

      explicit Key(const std::string& cipher_key_b64, const std::string& hmac_key_b64)
      {
	b64_to_key(cipher_key_b64, "cipher key", cipher_value_, CIPHER_KEY_SIZE);
	b64_to_key(hmac_key_b64, "hmac key", hmac_value_, HMAC_KEY_SIZE);
      }

      ~Key()
      {
	// wipe keys
	std::memset(cipher_value_, 0, CIPHER_KEY_SIZE);
	std::memset(hmac_value_, 0, HMAC_KEY_SIZE);
      }

      std::string to_string() const
      {
	return "TLSTicketKey[cipher=" + cipher_b64() + " hmac=" + hmac_b64() + ']';
      }

      std::string cipher_b64() const
      {
	return base64->encode(cipher_value_, CIPHER_KEY_SIZE);
      }

      std::string hmac_b64() const
      {
	return base64->encode(hmac_value_, HMAC_KEY_SIZE);
      }

      bool operator==(const Key& rhs) const
      {
	return std::memcmp(cipher_value_, rhs.cipher_value_, CIPHER_KEY_SIZE) == 0 && std::memcmp(hmac_value_, rhs.hmac_value_, HMAC_KEY_SIZE) == 0;
      }

      bool operator!=(const Key& rhs) const
      {
	return !operator==(rhs);
      }

      template <typename KEY_TRANSFORM>
      void key_transform(KEY_TRANSFORM& t)
      {
	unsigned char out[KEY_TRANSFORM::MAX_HMAC_SIZE];

	// cipher
	{
	  t.cipher_transform.reset();
	  t.cipher_transform.update(cipher_value_, CIPHER_KEY_SIZE);
	  const size_t size = t.cipher_transform.final(out);
	  if (size < CIPHER_KEY_SIZE)
	    throw sess_ticket_error("insufficient key material for cipher transform");
	  std::memcpy(cipher_value_, out, CIPHER_KEY_SIZE);
	}

	// hmac
	{
	  t.hmac_transform.reset();
	  t.hmac_transform.update(hmac_value_, HMAC_KEY_SIZE);
	  const size_t size = t.hmac_transform.final(out);
	  if (size < HMAC_KEY_SIZE)
	    throw sess_ticket_error("insufficient key material for hmac transform");
	  std::memcpy(hmac_value_, out, HMAC_KEY_SIZE);
	}
      }

    private:
      // we need to friend SSL implementation classes
      friend class OpenSSLContext;
      friend class MbedTLSContext;

      Key() {} // note that default constructor leaves object in an undefined state

      unsigned char cipher_value_[CIPHER_KEY_SIZE];
      unsigned char hmac_value_[HMAC_KEY_SIZE];
    };

    // method returns name and key
    virtual Status create_session_ticket_key(Name& name, Key& key) const = 0;

    // method is given name and returns key
    virtual Status lookup_session_ticket_key(const Name& name, Key& key) const = 0;

    // return string that identifies the app
    virtual std::string session_id_context() const = 0;

    virtual ~TLSSessionTicketBase() {}

  private:
    static void b64_to_key(const std::string& b64, const char *title, unsigned char *out, const size_t outlen)
    {
      Buffer srcbuf(out, outlen, false);
      try {
	base64->decode(srcbuf, b64);
      }
      catch (const std::exception& e)
	{
	  throw sess_ticket_error(std::string("base64 decode for ") + title + ": " + std::string(e.what()));
	}
      if (srcbuf.size() != outlen)
	throw sess_ticket_error(std::string("wrong input size for ") + title + ", actual=" + std::to_string(srcbuf.size()) + " expected=" + std::to_string(outlen));
    }
  };
}

#ifdef USE_OPENVPN_HASH
OPENVPN_HASH_METHOD(openvpn::TLSSessionTicketBase::Name, hashval);
#endif
