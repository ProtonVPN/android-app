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

// Emulate Excluded Routes implementation (needed by Android)

#ifndef OPENVPN_CLIENT_CLIEMUEXR_H
#define OPENVPN_CLIENT_CLIEMUEXR_H

#include <openvpn/common/exception.hpp>
#include <openvpn/tun/client/emuexr.hpp>
#include <openvpn/addr/routeinv.hpp>

namespace openvpn {
  class EmulateExcludeRouteImpl : public EmulateExcludeRoute
  {
  public:
    OPENVPN_EXCEPTION(emulate_exclude_route_error);

    typedef RCPtr<EmulateExcludeRouteImpl> Ptr;

    EmulateExcludeRouteImpl(const bool exclude_server_address)
      : exclude_server_address_(exclude_server_address)
    {
    }

  private:
    virtual void add_route(const bool add, const IP::Addr& addr, const int prefix_len)
    {
      (add ? include : exclude).emplace_back(addr, prefix_len);
    }

    virtual void add_default_routes(bool ipv4, bool ipv6)
    {
      if (ipv4)
	add_route(true, IP::Addr::from_zero(IP::Addr::V4), 0);
      if (ipv6)
	add_route(true, IP::Addr::from_zero(IP::Addr::V6), 0);
    }

    virtual bool enabled(const IPVerFlags& ipv) const
    {
      return exclude.size() && (ipv.rgv4() || ipv.rgv6());
    }

    virtual void emulate(TunBuilderBase* tb, IPVerFlags& ipv, const IP::Addr& server_addr) const
    {
      const unsigned int rg_ver_flags = ipv.rg_ver_flags();
      if (exclude.size() && rg_ver_flags)
	{
	  IP::RouteList rl;
	  rl.reserve(include.size() + exclude.size());
	  rl.insert(rl.end(), include.begin(), include.end());
	  rl.insert(rl.end(), exclude.begin(), exclude.end());

	  if (exclude_server_address_ && (server_addr.version_mask() & rg_ver_flags))
	    rl.emplace_back(server_addr, server_addr.size());

	  const IP::RouteInverter ri(rl, rg_ver_flags);
	  //OPENVPN_LOG("Exclude routes emulation:\n" << ri);
	  for (IP::RouteInverter::const_iterator i = ri.begin(); i != ri.end(); ++i)
	    {
	      const IP::Route& r = *i;
	      if (checkRouteShouldBeInstalled(r))
		if (!tb->tun_builder_add_route(r.addr.to_string(), r.prefix_len, -1, r.addr.version() == IP::Addr::V6))
		  throw emulate_exclude_route_error("tun_builder_add_route failed");
	    }

	  ipv.set_emulate_exclude_routes();
	}
    }

    bool checkRouteShouldBeInstalled(const IP::Route& r) const
      {
	IP::Route const* bestroute = nullptr;
	// Get the best (longest-prefix) route from included routes that matches
	for (const auto& incRoute: include)
	{
	  if (incRoute.contains (r))
	    {
	      if (bestroute == nullptr || bestroute->prefix_len < incRoute.prefix_len )
	        bestroute = &incRoute;
	    }
	}
	// No postive route matches the route at all, do not install it
	if (!bestroute)
	  return false;

	// Check if there is a more specific exclude route
	for (const auto& exclRoute: exclude)
	  {
	    if (exclRoute.contains (r) && exclRoute.prefix_len > bestroute->prefix_len)
	      return false;
	  }
        return true;
      }

    const bool exclude_server_address_;
    IP::RouteList include;
    IP::RouteList exclude;
  };

  class EmulateExcludeRouteFactoryImpl : public EmulateExcludeRouteFactory
  {
  public:
    typedef RCPtr<EmulateExcludeRouteFactoryImpl> Ptr;

    EmulateExcludeRouteFactoryImpl(const bool exclude_server_address)
      : exclude_server_address_(exclude_server_address)
    {
    }

  private:
    virtual EmulateExcludeRoute::Ptr new_obj() const
    {
      return EmulateExcludeRoute::Ptr(new EmulateExcludeRouteImpl(exclude_server_address_));
    }

    const bool exclude_server_address_;
  };
}

#endif
