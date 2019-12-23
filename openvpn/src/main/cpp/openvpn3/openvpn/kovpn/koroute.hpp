//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2018 OpenVPN Inc.
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

// OpenVPN 3 wrapper for kovpn

#ifndef OPENVPN_KOVPN_KOROUTE_H
#define OPENVPN_KOVPN_KOROUTE_H

#include <vector>
#include <memory>

#include <openvpn/kovpn/kovpn.hpp>
#include <openvpn/addr/route.hpp>

namespace openvpn {
  namespace KoRoute {
    inline struct ovpn_route from_route(const IP::Route& r)
    {
      struct ovpn_route ret;
      ret.prefix_len = r.prefix_len;
      ret.addr.v6 = (r.addr.version() == IP::Addr::V6);
      switch (r.addr.version())
	{
	case IP::Addr::V6:
	  ret.addr.u.a6 = r.addr.to_ipv6_nocheck().to_in6_addr();
	  break;
	case IP::Addr::V4:
	  ret.addr.u.a4 = r.addr.to_ipv4_nocheck().to_in_addr();
	  break;
	default:
	  throw IP::ip_exception("route address unspecified");
	}
      return ret;
    }

    inline struct ovpn_route *from_routes(const std::vector<IP::Route>& rtvec)
    {
      if (rtvec.size())
	{
	  std::unique_ptr<struct ovpn_route[]> routes(new ovpn_route[rtvec.size()]);
	  for (size_t i = 0; i < rtvec.size(); ++i)
	    routes[i] = from_route(rtvec[i]);
	  return routes.release();
	}
      else
	return nullptr;
    }
  }
}

#endif
