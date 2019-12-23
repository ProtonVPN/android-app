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

// Invert a route list.  Used to support excluded routes on platforms that
// don't support them natively.

#ifndef OPENVPN_ADDR_ROUTEINV_H
#define OPENVPN_ADDR_ROUTEINV_H

#include <openvpn/common/exception.hpp>
#include <openvpn/addr/route.hpp>

namespace openvpn {
  namespace IP {
    class RouteInverter : public RouteList
    {
    public:
      OPENVPN_EXCEPTION(route_inverter);

      RouteInverter() {}

      // NOTE: when passing RouteInverter to this constructor, make sure
      // to static_cast it to RouteList& so as to avoid matching the
      // default copy constructor.
      explicit RouteInverter(const RouteList& in)
	: RouteInverter(in, in.version_mask())
      {
      }

      RouteInverter(const RouteList& in, const Addr::VersionMask vermask)
      {
	in.verify_canonical();
	if (vermask & Addr::V4_MASK)
	  descend(in, Addr::V4, Route(Addr::from_zero(Addr::V4), 0));
	if (vermask & Addr::V6_MASK)
	  descend(in, Addr::V6, Route(Addr::from_zero(Addr::V6), 0));
      }

    private:
      enum Type {
	EQUAL,
	SUBROUTE,
	LEAF,
      };
      /**
       * This method construct a non-overlapping list of routes span the address
       * space in @param route.  The routes are constructed in a way that each
       * route in the returned list is smaller or equalto each route in
       * parameter @param in
       *
       * @param ver IP version
       * @param route The route we currently are looking at and split if it does
       * 	      not meet the requirements
       */
      void descend(const RouteList& in, const Addr::Version ver, const Route& route)
      {
	switch (find(in, route))
	  {
	  case SUBROUTE:
	    {
	      Route r1, r2;
	      if (route.split(r1, r2))
		{
		  descend(in, ver, r1);
		  descend(in, ver, r2);
		}
	      else
		push_back(route);
	      break;
	    }
	  case EQUAL:
	  case LEAF:
	    push_back(route);
	    break;
	  }
      }

      static Type find(const RouteList& in, const Route& route)
      {
	Type type = LEAF;
	for (RouteList::const_iterator i = in.begin(); i != in.end(); ++i)
	  {
	    const Route& r = *i;
	    if (route == r)
	      type = EQUAL;
	    else if (route.contains(r))
	      return SUBROUTE;
	  }
	return type;
      }
    };
  }
}

#endif
