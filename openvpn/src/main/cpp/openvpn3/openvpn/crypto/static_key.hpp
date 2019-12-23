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

// Classes for handling OpenVPN static keys (and tls-auth keys)

#ifndef OPENVPN_CRYPTO_STATIC_KEY_H
#define OPENVPN_CRYPTO_STATIC_KEY_H

#include <string>
#include <sstream>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/hexstr.hpp>
#include <openvpn/common/file.hpp>
#include <openvpn/common/splitlines.hpp>
#include <openvpn/common/base64.hpp>
#include <openvpn/buffer/buffer.hpp>
#include <openvpn/random/randapi.hpp>

namespace openvpn {

  class StaticKey
  {
    friend class OpenVPNStaticKey;
    typedef BufferAllocated key_t;

  public:
    StaticKey() {}
    StaticKey(const unsigned char *key_data, const size_t key_size)
      : key_data_(key_data, key_size, key_t::DESTRUCT_ZERO) {}

    size_t size() const { return key_data_.size(); }
    const unsigned char *data() const { return key_data_.c_data(); }
    void erase() { key_data_.clear(); }

    std::string render_hex() const { return openvpn::render_hex_generic(key_data_); }

    void parse_from_base64(const std::string& b64, const size_t capacity)
    {
      key_data_.reset(capacity, key_t::DESTRUCT_ZERO);
      base64->decode(key_data_, b64);
    }

    std::string render_to_base64() const
    {
      return base64->encode(key_data_);
    }

    void init_from_rng(RandomAPI& rng, const size_t key_size)
    {
      rng.assert_crypto();
      key_data_.init(key_size, key_t::DESTRUCT_ZERO);
      rng.rand_bytes(key_data_.data(), key_size);
      key_data_.set_size(key_size);
    }

  private:
    key_t key_data_;
  };

  class OpenVPNStaticKey
  {
    typedef StaticKey::key_t key_t;

  public:
    enum {
      KEY_SIZE = 256 // bytes
    };

    // key specifier
    enum {
      // key for cipher and hmac
      CIPHER = 0,
      HMAC = (1<<0),

      // do we want to encrypt or decrypt with this key
      ENCRYPT = 0,
      DECRYPT = (1<<1),

      // key direction
      NORMAL = 0,
      INVERSE = (1<<2)
    };

    OPENVPN_SIMPLE_EXCEPTION(static_key_parse_error);
    OPENVPN_SIMPLE_EXCEPTION(static_key_bad_size);

    bool defined() const { return key_data_.defined(); }

    StaticKey slice(unsigned int key_specifier) const
    {
      if (key_data_.size() != KEY_SIZE)
	throw static_key_bad_size();
      static const unsigned char key_table[] = { 0, 1, 2, 3, 2, 3, 0, 1 };
      const unsigned int idx = key_table[key_specifier & 7] * 64;
      return StaticKey(key_data_.c_data() + idx, KEY_SIZE / 4);
    }

    void parse_from_file(const std::string& filename)
    {
      const std::string str = read_text(filename);
      parse(str);
    }

    void parse(const std::string& key_text)
    {
      SplitLines in(key_text, 0);
      key_t data(KEY_SIZE, key_t::DESTRUCT_ZERO);
      bool in_body = false;
      while (in(true))
	{
	  const std::string& line = in.line_ref();
	  if (line == static_key_head())
	    in_body = true;
	  else if (line == static_key_foot())
	    in_body = false;
	  else if (in_body)
	    parse_hex(data, line);
	}
      if (in_body || data.size() != KEY_SIZE)
	throw static_key_parse_error();
      key_data_ = data;
    }

    std::string render() const
    {
      if (key_data_.size() != KEY_SIZE)
	throw static_key_bad_size();
      std::ostringstream out;
      out << static_key_head() << "\n";
      for (size_t i = 0; i < KEY_SIZE; i += 16)
	out << render_hex(key_data_.c_data() + i, 16) << "\n";
      out << static_key_foot() << "\n";
      return out.str();
    }

    unsigned char *raw_alloc()
    {
      key_data_.init(KEY_SIZE, key_t::DESTRUCT_ZERO|key_t::ARRAY);
      return key_data_.data();
    }

    void erase()
    {
      key_data_.clear();
    }

  private:
    static const char *static_key_head()
    {
      return "-----BEGIN OpenVPN Static key V1-----";
    }

    static const char *static_key_foot()
    {
      return "-----END OpenVPN Static key V1-----";
    }

    key_t key_data_;
  };


} // namespace openvpn

#endif // OPENVPN_CRYPTO_STATIC_KEY_H
