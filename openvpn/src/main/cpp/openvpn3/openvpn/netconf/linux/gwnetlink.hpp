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

// Find default gateways on Linux using ip route command

#pragma once

#include <string>

#include <openvpn/common/exception.hpp>
#include <openvpn/addr/ip.hpp>
#include <openvpn/addr/ipv4.hpp>
#include <openvpn/addr/ipv6.hpp>
#include <openvpn/tun/linux/client/sitnl.hpp>

namespace openvpn {
  class LinuxGWNetlink
  {
  public:
    OPENVPN_EXCEPTION(linux_gw_netlink_error);

    LinuxGWNetlink(bool ipv6)
    {
      try
      {
	if (ipv6)
	{
	  IPv6::Addr addr6;

	  if (TunNetlink::SITNL::net_route_best_gw(IP::Route6(IPv6::Addr::from_zero(), 0),
						   addr6, dev_) < 0)
	  {
	    OPENVPN_THROW(linux_gw_netlink_error,
			  "error retrieving default IPv6 GW");
	  }

	  addr_ = IP::Addr::from_ipv6(addr6);
	}
	else
	{
	  IPv4::Addr addr4;

	  if (TunNetlink::SITNL::net_route_best_gw(IP::Route4(IPv4::Addr::from_zero(), 0),
						   addr4, dev_) < 0)
	  {
	    OPENVPN_THROW(linux_gw_netlink_error,
			  "error retrieving default IPv4 GW");
	  }

	  addr_ = IP::Addr::from_ipv4(addr4);
	}
      } catch (...)
      {
	/* nothing to do. just leave default GW unassigned */
      }
    }

    const std::string& dev() const
    {
      return dev_;
    }

    const IP::Addr& addr() const
    {
      return addr_;
    }

    bool defined() const
    {
      return !dev_.empty() && addr_.defined();
    }

    std::string to_string() const
    {
      return dev_ + '/' + addr_.to_string();
    }

  private:
    IP::Addr addr_;
    std::string dev_;
  };

  struct LinuxGW46Netlink
  {
    LinuxGW46Netlink()
      : v4(false),
        v6(true)
    {
    }

    std::string to_string() const
    {
      std::string ret = "[";
      if (v4.defined())
	{
	  ret += "4:";
	  ret += v4.to_string();
	}
      if (v6.defined())
	{
	  if (v4.defined())
	    ret += ' ';
	  ret += "6:";
	  ret += v6.to_string();
	}
      ret += "]";
      return ret;
    }

    std::string dev() const
    {
      if (v4.defined())
	return v4.dev();
      else if (v6.defined())
	return v6.dev();
      else
	throw LinuxGWNetlink::linux_gw_netlink_error("cannot determine gateway interface");
    }

    LinuxGWNetlink v4;
    LinuxGWNetlink v6;
  };
}
