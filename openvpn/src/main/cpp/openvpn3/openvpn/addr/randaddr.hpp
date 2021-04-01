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

#include <openvpn/addr/route.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/random/randapi.hpp>

namespace openvpn {
  namespace IP {

    inline IPv4::Addr random_addr_v4(RandomAPI& prng)
    {
      return IPv4::Addr::from_uint32(prng.rand_get<std::uint32_t>());
    }

    inline IPv6::Addr random_addr_v6(RandomAPI& prng)
    {
      unsigned char bytes[16];
      prng.rand_fill(bytes);
      return IPv6::Addr::from_byte_string(bytes);
    }

    inline Addr random_addr(const Addr::Version v, RandomAPI& prng)
    {
      switch (v)
	{
	case Addr::V4:
	  return Addr::from_ipv4(random_addr_v4(prng));
	case Addr::V6:
	  return Addr::from_ipv6(random_addr_v6(prng));
	default:
	  throw ip_exception("address unspecified");
	}
    }

    // bit positions between templ.prefix_len and prefix_len are randomized
    inline Route random_subnet(const Route& templ,
			       const unsigned int prefix_len,
			       RandomAPI& prng)
    {
      if (!templ.is_canonical())
	throw Exception("IP::random_subnet: template route not canonical: " + templ.to_string());
      return Route(((random_addr(templ.addr.version(), prng) & ~templ.netmask()) | templ.addr)
		   & Addr::netmask_from_prefix_len(templ.addr.version(), prefix_len),
		   prefix_len);
    }
  }
}
