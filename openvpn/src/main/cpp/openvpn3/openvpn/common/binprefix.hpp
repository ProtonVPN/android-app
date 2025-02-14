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

#ifndef OPENVPN_COMMON_BINPREFIX_H
#define OPENVPN_COMMON_BINPREFIX_H

#include <algorithm> // for std::min, std::max
#include <cstring>   // for std::memset, std::memcpy
#include <cstdint>   // for std::uint32_t, uint64_t

#include <openvpn/common/socktypes.hpp> // for ntohl

namespace openvpn {

// Return the binary prefix of a big-endian data buffer
// as a 32 bit type.
template <typename T>
inline typename std::enable_if<4 == sizeof(T), T>::type
bin_prefix(const unsigned char *data)
{
    static_assert(sizeof(T) == 4, "size inconsistency");
    return T(ntohl(*(uint32_t *)&data[0]));
}

// Return the binary prefix of a big-endian data buffer
// as a 64 bit type.
template <typename T>
inline typename std::enable_if<8 == sizeof(T), T>::type
bin_prefix(const unsigned char *data)
{
    static_assert(sizeof(T) == 8, "size inconsistency");
    return (T(ntohl(*(uint32_t *)&data[0])) << 32) | T(ntohl(*(uint32_t *)&data[4]));
}

template <typename T>
inline T bin_prefix(const unsigned char *data, const size_t len)
{
    unsigned char d[sizeof(T)]
#ifndef _MSC_VER
        __attribute__((aligned(sizeof(T))))
#endif
        ;
    const size_t l = std::min(len, sizeof(d));
    std::memset(d, 0, sizeof(d));
    std::memcpy(d + sizeof(d) - l, data, l);
    return bin_prefix<T>(d);
}

template <typename T>
inline T bin_prefix_floor(const unsigned char *data, const size_t len, const T floor)
{
    return std::max(bin_prefix<T>(data, len), floor);
}

} // namespace openvpn
#endif
