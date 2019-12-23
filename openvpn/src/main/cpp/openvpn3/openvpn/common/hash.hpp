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

#ifndef OPENVPN_COMMON_HASH_H
#define OPENVPN_COMMON_HASH_H

#include <string>
#include <cstdint> // for std::uint32_t, uint64_t

#include <openvpn/common/exception.hpp>
#include <openvpn/common/size.hpp>
#include <openvpn/common/hexstr.hpp>

#define OPENVPN_HASH_METHOD(T, meth)			\
  namespace std {					\
    template <>						\
    struct hash<T>					\
    {							\
      inline std::size_t operator()(const T& obj) const	\
      {							\
        return obj.meth();				\
      }							\
    };							\
  }

#ifdef HAVE_CITYHASH

#ifdef OPENVPN_HASH128_CRC
#include <citycrc.h>
#define OPENVPN_HASH128 ::CityHashCrc128WithSeed
#else
#include <city.h>
#define OPENVPN_HASH128 ::CityHash128WithSeed
#endif

#if SIZE_MAX == 0xFFFFFFFF
#define HashSizeT Hash32
#elif SIZE_MAX == 0xFFFFFFFFFFFFFFFF
#define HashSizeT Hash64
#else
#error "Unrecognized SIZE_MAX"
#endif

namespace openvpn {

  class Hash128
  {
  public:
    Hash128() : hashval(0,0) {}

    void operator()(const void *data, const std::size_t size)
    {
      hashval = OPENVPN_HASH128((const char *)data, size, hashval);
    }

    void operator()(const std::string& str)
    {
      (*this)(str.c_str(), str.length());
    }

    template <typename T>
    inline void operator()(const T& obj)
    {
      static_assert(std::is_pod<T>::value, "Hash128: POD type required");
      (*this)(&obj, sizeof(obj));
    }

    std::uint64_t high() const
    {
      return hashval.second;
    }

    std::uint64_t low() const
    {
      return hashval.first;
    }

    std::string to_string() const
    {
      return render_hex_number(high()) + render_hex_number(low());
    }

  private:
    uint128 hashval;
  };

  class Hash64
  {
  public:
    Hash64(const std::uint64_t init_hashval=0)
      : hashval(init_hashval)
    {
    }

    void operator()(const void *data, const std::size_t size)
    {
      hashval = ::CityHash64WithSeed((const char *)data, size, hashval);
    }

    void operator()(const std::string& str)
    {
      (*this)(str.c_str(), str.length());
    }

    template <typename T>
    inline void operator()(const T& obj)
    {
      static_assert(std::is_pod<T>::value, "Hash64: POD type required");
      (*this)(&obj, sizeof(obj));
    }

    std::uint64_t value() const
    {
      return hashval;
    }

    std::string to_string() const
    {
      return render_hex_number(hashval);
    }

  private:
    std::uint64_t hashval;
  };

  class Hash32
  {
  public:
    Hash32(const std::uint32_t init_hashval=0)
      : hashval(init_hashval)
    {
    }

    void operator()(const void *data, const std::size_t size)
    {
      hashval = hash_combine(::CityHash32((const char *)data, size), hashval);
    }

    void operator()(const std::string& str)
    {
      (*this)(str.c_str(), str.length());
    }

    template <typename T>
    inline void operator()(const T& obj)
    {
      static_assert(std::is_pod<T>::value, "Hash64: POD type required");
      (*this)(&obj, sizeof(obj));
    }

    std::uint32_t value() const
    {
      return hashval;
    }

    std::string to_string() const
    {
      return render_hex_number(hashval);
    }

  private:
    static std::uint32_t hash_combine(const std::uint32_t h1,
				      const std::uint32_t h2)
    {
      return h1 ^ (h2 + 0x9e3779b9 + (h1<<6) + (h1>>2));
    }

    std::uint32_t hashval;
  };

}

#endif
#endif
