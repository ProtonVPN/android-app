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

// API for random number implementations.

#pragma once

#include <string>
#include <cstdint>

#include <openvpn/common/size.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/random/randistrib.hpp>

namespace openvpn {

  class RandomAPI : public RC<thread_unsafe_refcount>
  {
  public:
    typedef RCPtr<RandomAPI> Ptr;

    // Random algorithm name
    virtual std::string name() const = 0;

    // Return true if algorithm is crypto-strength
    virtual bool is_crypto() const = 0;

    // Fill buffer with random bytes
    virtual void rand_bytes(unsigned char *buf, size_t size) = 0;

    // Like rand_bytes, but don't throw exception.
    // Return true on successs, false on fail.
    virtual bool rand_bytes_noexcept(unsigned char *buf, size_t size) = 0;

    // Fill a data object with random bits
    template <typename T>
    void rand_fill(T& obj)
    {
      rand_bytes(reinterpret_cast<unsigned char *>(&obj), sizeof(T));
    }

    // Return a data object with random bits
    template <typename T>
    T rand_get()
    {
      T ret;
      rand_fill(ret);
      return ret;
    }

    // Return a data object with random bits, always >= 0 for signed types
    template <typename T>
    T rand_get_positive()
    {
      T ret = rand_get<T>();
      if (ret < 0)
	ret = -ret;
      return ret;
    }

    // Return a uniformly distributed random number in the range [0, end).
    // end must be > 0.
    template <typename T>
    T randrange(const T end)
    {
      return rand_get_positive<T>() % end;
    }

    // Return a uniformly distributed random number in the range [start, end].
    template <typename T>
    T randrange(const T start, const T end)
    {
      if (start >= end)
	return start;
      else
	return start + rand_get_positive<T>() % (end - start + 1);
    }

    // Return a uniformly distributed random number in the range [0, end).
    // This version is strictly 32-bit only and optimizes by avoiding
    // integer division.
    std::uint32_t randrange32(const std::uint32_t end)
    {
      std::uint32_t r;
      rand_fill(r);
      return rand32_distribute(r, end);
    }

    // Return a uniformly distributed random number in the range [start, end].
    // This version is strictly 32-bit only and optimizes by avoiding
    // integer division.
    std::uint32_t randrange32(const std::uint32_t start, const std::uint32_t end)
    {
      if (start >= end)
	return start;
      else
	return start + randrange32(end - start + 1);
    }

    // Return a random byte
    std::uint8_t randbyte()
    {
      std::uint8_t byte;
      rand_fill(byte);
      return byte;
    }

    // Return a random boolean
    bool randbool()
    {
      return bool(randbyte() & 1);
    }

    // Throw an exception if algorithm is not crypto-strength.
    // Be sure to always call this method before using an rng
    // for crypto purposes.
    void assert_crypto() const
    {
      if (!is_crypto())
	throw Exception("RandomAPI: " + name() + " algorithm is not crypto-strength");
    }

    // UniformRandomBitGenerator for std::shuffle
    typedef unsigned int result_type;
    static constexpr result_type min() { return result_type(0); }
    static constexpr result_type max() { return ~result_type(0); }
    result_type operator()() { return rand_get<result_type>(); }
  };

}
