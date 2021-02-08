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

// A general-purpose Session ID class

#ifndef OPENVPN_COMMON_SESS_ID_H
#define OPENVPN_COMMON_SESS_ID_H

#include <string>
#include <cstring>
#include <cstdint> // for std::uint8_t, std::uint64_t

#include <openvpn/common/exception.hpp>
#include <openvpn/common/size.hpp>
#include <openvpn/common/hash.hpp>
#include <openvpn/common/ostream.hpp>
#include <openvpn/common/base64.hpp>
#include <openvpn/common/arraysize.hpp>
#include <openvpn/buffer/buffer.hpp>
#include <openvpn/random/randapi.hpp>

namespace openvpn {
  template <size_t SIZE>
  class SessionIDType
  {
  public:
    template <size_t S> friend class SessionIDType;

    // Create a zeroed Sesson ID.
    SessionIDType()
    {
      // compile-time size constraints
      static_assert(sizeof(u.data) >= sizeof(std::uint64_t), "SessionIDType SIZE too small");
      static_assert(SIZE % sizeof(std::uint64_t) == size_t(0), "SessionIDType SIZE must be an integer multiple of 64 bits");
      std::memset(u.data, 0, sizeof(u.data));
    }

    // Create a random Session ID.
    explicit SessionIDType(RandomAPI& rng, const bool allow_noncrypto_rng=false)
    {
      if (!allow_noncrypto_rng)
	rng.assert_crypto();
      rng.rand_bytes(u.data, sizeof(u.data));
    }

    // Create a Session ID from a base64 (URL-safe) string.
    explicit SessionIDType(const std::string& b64)
    {
      Buffer srcbuf(u.data, sizeof(u.data), false);
      try {
	base64_urlsafe->decode(srcbuf, b64);
      }
      catch (const std::exception& e)
	{
	  throw Exception("SessionID: base64 decode: " + std::string(e.what()));
	}
      if (srcbuf.size() != sizeof(u.data))
	throw Exception("SessionID: wrong input size, actual=" + std::to_string(srcbuf.size()) + " expected=" + std::to_string(sizeof(u.data)));
    }

    // Create a Session ID from a byte string of size size().
    explicit SessionIDType(const std::uint8_t* bytes)
    {
      std::memcpy(u.data, bytes, SIZE);
    }

    // Create a Session ID from another Session ID of possibly
    // different size.  If the other Session ID is larger,
    // truncate, if it's smaller, zero our tail.
    template <size_t S>
    explicit SessionIDType(const SessionIDType<S>& other)
    {
      for (size_t i = 0; i < array_size(u.dataz); ++i)
	u.dataz[i] = (i < array_size(other.u.dataz)) ? other.u.dataz[i] : 0;
    }

    // Create an encrypted Session ID.
    // Intended to be used with TokenEncrypt.
    template <typename CRYPT>
    explicit SessionIDType(const SessionIDType& other, CRYPT& crypt)
    {
      crypt(u.data, other.u.data, SIZE);
    }

    // Session ID is considered to be undefined if all bits are zero.
    bool defined() const
    {
      for (size_t i = 0; i < array_size(u.dataz); ++i)
	if (u.dataz[i])
	  return true;
      return false;
    }

    // Return the lower 64 bits of Session ID regardless of the size.
    std::uint64_t shortform() const
    {
      return u.dataz[0];
    }

    template <typename HASH>
    void hash(HASH& h) const
    {
      h(u.dataz[0]);
    }

    // Use a URL-safe base64 encoding.
    std::string to_string() const
    {
      return base64_urlsafe->encode(u.data, sizeof(u.data));
    }

    bool operator==(const SessionIDType& other) const
    {
      return std::memcmp(u.data, other.u.data, sizeof(u.data)) == 0;
    }

    bool operator!=(const SessionIDType& other) const
    {
      return !operator==(other);
    }

    bool operator<(const SessionIDType& other) const
    {
      return std::memcmp(u.data, other.u.data, sizeof(u.data)) < 0;
    }

    // Weak equality means that the lower 64 bits compare equal.
    template <size_t S>
    bool eq_weak(const SessionIDType<S>& other) const
    {
      return shortform() == other.shortform();
    }

    // True if the string looks like a Session ID.
    static bool is(const std::string& str)
    {
      return base64_urlsafe->is_base64(str, SIZE);
    }

    static constexpr size_t size()
    {
      return SIZE;
    }

    const std::uint8_t* c_data() const
    {
      return u.data;
    }

    // Find an element in an unordered map (keyed by Session ID)
    // using weak equality.  If conflict is true, only return
    // element that is present by weak equality, but which is
    // not equal to *this by strong equality.
    template <typename UNORDERED_MAP>
    const SessionIDType* find_weak(const UNORDERED_MAP& m, const bool conflict) const
    {
      if (m.bucket_count())
	{
	  const size_t bi = m.bucket(*this);
	  for (auto i = m.cbegin(bi); i != m.cend(bi); ++i)
	    if (shortform() == i->first.shortform() && (!conflict || *this != i->first))
	      return &i->first;
	}
      return nullptr;
    }

  private:
    union {
      std::uint64_t dataz[SIZE / sizeof(std::uint64_t)];
      std::uint8_t data[SIZE];
    } u;
  };

  // Create two concrete types: 64 and 128-bit Session IDs.
  typedef SessionIDType<8> SessionID64;
  typedef SessionIDType<16> SessionID128;

  OPENVPN_OSTREAM(SessionID64, to_string);
  OPENVPN_OSTREAM(SessionID128, to_string);
}

OPENVPN_HASH_METHOD(openvpn::SessionID64, shortform);
OPENVPN_HASH_METHOD(openvpn::SessionID128, shortform);

#endif
