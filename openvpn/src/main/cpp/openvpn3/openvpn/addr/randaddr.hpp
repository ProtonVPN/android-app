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

#include <openvpn/addr/route.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/random/randapi.hpp>

namespace openvpn::IP {

inline IPv4::Addr random_addr_v4(RandomAPI &prng)
{
    return IPv4::Addr::from_uint32(prng.rand_get<std::uint32_t>());
}

inline IPv6::Addr random_addr_v6(RandomAPI &prng)
{
    unsigned char bytes[16];
    prng.rand_fill(bytes);
    return IPv6::Addr::from_byte_string(bytes);
}

inline Addr random_addr(const Addr::Version v, RandomAPI &prng)
{
    switch (v)
    {
    case Addr::V4:
        return Addr::from_ipv4(random_addr_v4(prng));
    case Addr::V6:
        return Addr::from_ipv6(random_addr_v6(prng));
    default:
        OPENVPN_IP_THROW("random_addr: address unspecified");
    }
}

// bit positions between templ.prefix_len and prefix_len are randomized
inline Route random_subnet(const Route &templ,
                           const unsigned int prefix_len,
                           RandomAPI &prng)
{
    if (!templ.is_canonical())
        throw Exception("IP::random_subnet: template route not canonical: " + templ.to_string());
    return Route(((random_addr(templ.addr.version(), prng) & ~templ.netmask()) | templ.addr)
                     & Addr::netmask_from_prefix_len(templ.addr.version(), prefix_len),
                 prefix_len);
}
} // namespace openvpn::IP
