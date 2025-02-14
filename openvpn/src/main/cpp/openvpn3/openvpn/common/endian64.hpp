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

#include <cstdint>

#include <openvpn/common/endian_platform.hpp>

namespace openvpn::Endian {
#ifdef __MINGW32__
inline std::uint64_t mingw_bswap64(const std::uint64_t val)
{
    return (((val & (uint64_t)0x00000000000000ffULL) << 56)
            | ((val & (uint64_t)0x000000000000ff00ULL) << 40)
            | ((val & (uint64_t)0x0000000000ff0000ULL) << 24)
            | ((val & (uint64_t)0x00000000ff000000ULL) << 8)
            | ((val & (uint64_t)0x000000ff00000000ULL) >> 8)
            | ((val & (uint64_t)0x0000ff0000000000ULL) >> 24)
            | ((val & (uint64_t)0x00ff000000000000ULL) >> 40)
            | ((val & (uint64_t)0xff00000000000000ULL) >> 56));
}
#endif
inline std::uint64_t rev64(const std::uint64_t value)
{
#ifdef OPENVPN_LITTLE_ENDIAN
#if defined(_MSC_VER)
    return _byteswap_uint64(value);
#elif defined(__MINGW32__)
    return mingw_bswap64(value);
#elif defined(__clang__) || !defined(__GLIBC__)
    return __builtin_bswap64(value);
#else
    return __bswap_constant_64(value);
#endif /* _MSC_VER */
#else
    return value;
#endif /* OPENVPN_LITTLE_ENDIAN */
}
} // namespace openvpn::Endian
