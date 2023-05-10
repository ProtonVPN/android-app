//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2022 OpenVPN Inc.
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

#include <string>
#include <cstdint> // for std::uint32_t, uint64_t

#ifdef HAVE_XXHASH
#define USE_OPENVPN_HASH
#define XXH_INLINE_ALL
#include <xxhash.h>
#if XXH_VERSION_NUMBER < 700
#error requires XXHash version 0.7.0 or higher
#endif
#endif

#include <openvpn/common/exception.hpp>
#include <openvpn/common/size.hpp>
#include <openvpn/common/hexstr.hpp>

#define OPENVPN_HASH_METHOD(T, meth)                      \
    namespace std {                                       \
    template <>                                           \
    struct hash<T>                                        \
    {                                                     \
        inline std::size_t operator()(const T &obj) const \
        {                                                 \
            return obj.meth();                            \
        }                                                 \
    };                                                    \
    }

#ifdef USE_OPENVPN_HASH

namespace openvpn {

class Hash64
{
  public:
    Hash64(const std::uint64_t init_hashval = 0)
        : hashval(init_hashval)
    {
    }

    void operator()(const void *data, const std::size_t size)
    {
        hashval = XXH3_64bits_withSeed((const char *)data, size, hashval);
    }

    void operator()(const std::string &str)
    {
        (*this)(str.c_str(), str.length());
    }

    template <typename T>
    inline void operator()(const T &obj)
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

} // namespace openvpn

#endif
