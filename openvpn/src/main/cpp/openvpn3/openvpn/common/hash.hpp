//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012- OpenVPN Inc.
//
//    SPDX-License-Identifier: MPL-2.0 OR AGPL-3.0-only WITH openvpn3-openssl-exception
//

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

#define OPENVPN_HASH_METHOD(T, meth)                        \
    namespace std {                                         \
    template <>                                             \
    struct hash<T>                                          \
    {                                                       \
        inline std::uint64_t operator()(const T &obj) const \
        {                                                   \
            return obj.meth();                              \
        }                                                   \
    };                                                      \
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
        static_assert(std::is_standard_layout_v<T>, "Hash64: standard layout required");
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
