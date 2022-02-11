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

#include <sys/ioctl.h>
#include <fcntl.h>
#include <errno.h>
#include <net/if.h>
#include <linux/if_tun.h>

#include <openvpn/asio/asioerr.hpp>
#include <openvpn/netconf/linux/gwnetlink.hpp>
#include <openvpn/common/action.hpp>
#include <openvpn/tun/builder/setup.hpp>
#include <openvpn/tun/client/tunbase.hpp>
#include <openvpn/tun/client/tunconfigflags.hpp>
#include <openvpn/tun/linux/client/sitnl.hpp>
#include <openvpn/tun/linux/client/tunsetup.hpp>

namespace openvpn {
  namespace TunNetlink {

    using namespace openvpn::TunLinuxSetup;

    struct NetlinkLinkSet : public Action
    {
      typedef RCPtr<NetlinkLinkSet> Ptr;

      NetlinkLinkSet() {}

      NetlinkLinkSet(std::string dev_arg, bool up_arg, int mtu_arg)
	: dev(dev_arg),
	  up(up_arg),
	  mtu(mtu_arg)
      {
      }

      NetlinkLinkSet* copy() const
      {
	NetlinkLinkSet *ret = new NetlinkLinkSet;
	ret->dev = dev;
	ret->up = up;
	ret->mtu = mtu;
	return ret;
      }

      virtual void execute(std::ostream& os) override
      {
	int ret;

	if (dev.empty())
	{
	  os << "Error: can't call NetlinkLinkSet with no interface" << std::endl;
	  return;
	}

	ret = SITNL::net_iface_mtu_set(dev, mtu);
	if (ret)
	{
	  os << "Error while executing NetlinkLinkSet " << dev << " mtu " << mtu
	    << ": " << ret << std::endl;
	}

	ret = SITNL::net_iface_up(dev, up);
	if (ret)
	{
	  os << "Error while executing NetlinkLinkSet " << dev << " up " << up
	    << ": " << ret << std::endl;
	}
      }

      virtual std::string to_string() const override
      {
	std::ostringstream os;
	os << "netlink iface " << dev << " link set " << up << " mtu " << mtu;
	return os.str();
      }

      std::string dev;
      bool up = true;
      int mtu = 1500;
    };

    struct NetlinkAddr4 : public Action
    {
      typedef RCPtr<NetlinkAddr4> Ptr;

      NetlinkAddr4() {}

      NetlinkAddr4(std::string dev_arg, IPv4::Addr& addr_arg, int prefixlen_arg,
		   IPv4::Addr& broadcast_arg, bool add_arg)
	: dev(dev_arg),
	  addr(addr_arg),
	  prefixlen(prefixlen_arg),
	  broadcast(broadcast_arg),
	  add(add_arg)
      {
      }

      NetlinkAddr4* copy() const
      {
	NetlinkAddr4 *ret = new NetlinkAddr4;
	ret->dev = dev;
	ret->addr = addr;
	ret->prefixlen = prefixlen;
	ret->broadcast = broadcast;
	ret->add = add;
	return ret;
      }

      virtual void execute(std::ostream& os) override
      {
	int ret;

	if (dev.empty())
	{
	  os << "Error: can't call NetlinkAddr4 with no interface" << std::endl;
	  return;
	}

	if (add)
	{
	  ret = SITNL::net_addr_add(dev, addr, prefixlen, broadcast);
	}
	else
	{
	  ret = SITNL::net_addr_del(dev, addr, prefixlen);
	}

	if (ret)
	{
	  os << "Error while executing NetlinkAddr4(add: " << add << ") "
	    << dev << ": " << ret << std::endl;
	}
      }

      virtual std::string to_string() const override
      {
	std::ostringstream os;
	os << "netlink iface " << dev << " " << (add ? "add" : "del") << " "
	  << addr.to_string() << "/" << prefixlen << " broadcast "
	  << broadcast.to_string();
	return os.str();
      }

      std::string dev;
      IPv4::Addr addr;
      int prefixlen = 0;
      IPv4::Addr broadcast;
      bool add = true;
    };

    struct NetlinkAddr6 : public Action
    {
      typedef RCPtr<NetlinkAddr6> Ptr;

      NetlinkAddr6() {}

      NetlinkAddr6(std::string dev_arg, IPv6::Addr& addr_arg, int prefixlen_arg,
		   bool add_arg)
	: dev(dev_arg),
	  addr(addr_arg),
	  prefixlen(prefixlen_arg),
	  add(add_arg)
      {
      }

      NetlinkAddr6* copy() const
      {
	NetlinkAddr6 *ret = new NetlinkAddr6;
	ret->dev = dev;
	ret->addr = addr;
	ret->prefixlen = prefixlen;
	ret->add = add;
	return ret;
      }

      virtual void execute(std::ostream& os) override
      {
	int ret;

	if (dev.empty())
	{
	  os << "Error: can't call NetlinkAddr6 with no interface" << std::endl;
	  return;
	}

	if (add)
	{
	  ret = SITNL::net_addr_add(dev, addr, prefixlen);
	}
	else
	{
	  ret = SITNL::net_addr_del(dev, addr, prefixlen);
	}

	if (ret)
	{
	  os << "Error while executing NetlinkAddr6(add: " << add << ") "
	    << dev << ": " << ret << std::endl;
	}
      }

      virtual std::string to_string() const override
      {
	std::ostringstream os;
	os << "netlink iface " << dev << " " << (add ? "add" : "del") << " "
	  << addr.to_string() << "/" << prefixlen;
	return os.str();
      }

      std::string dev;
      IPv6::Addr addr;
      int prefixlen = 0;
      bool add = true;
    };

    struct NetlinkAddr4PtP : public Action
    {
      typedef RCPtr<NetlinkAddr4PtP> Ptr;

      NetlinkAddr4PtP() {}

      NetlinkAddr4PtP(std::string dev_arg, IPv4::Addr local_arg,
		       IPv4::Addr remote_arg, bool add_arg)
	: dev(dev_arg),
	  local(local_arg),
	  remote(remote_arg),
	  add(add_arg)
      {
      }

      NetlinkAddr4PtP* copy() const
      {
	NetlinkAddr4PtP *ret = new NetlinkAddr4PtP;
	ret->dev = dev;
	ret->local = local;
	ret->remote = remote;
	ret->add = add;
	return ret;
      }

      virtual void execute(std::ostream& os) override
      {
	int ret;

	if (dev.empty())
	{
	  os << "Error: can't call NetlinkAddr4PtP with no interface" << std::endl;
	  return;
	}

	if (add)
	{
	  ret = SITNL::net_addr_ptp_add(dev, local, remote);
	}
	else
	{
	  ret = SITNL::net_addr_ptp_del(dev, local, remote);
	}

	if (ret)
	{
	  os << "Error while executing NetlinkAddr4PtP(add: " << add << ") "
	    << dev << ": " << ret << std::endl;
	}
      }

      virtual std::string to_string() const override
      {
	return "netlink iface " + dev + " " + (add ? "add" : "del") + " ptp "
	  + local.to_string() + " remote " + remote.to_string();
      }

      std::string dev;
      IPv4::Addr local;
      IPv4::Addr remote;
      bool add = true;
    };

    struct NetlinkRoute4 : public Action
    {
      typedef RCPtr<NetlinkRoute4> Ptr;

      NetlinkRoute4() {}

      NetlinkRoute4(IPv4::Addr& dst_arg, int prefixlen_arg, IPv4::Addr& gw_arg,
		    std::string dev_arg, bool add_arg)
	: route(dst_arg, prefixlen_arg),
	  gw(gw_arg),
	  dev(dev_arg),
	  add(add_arg)
      {
      }

      NetlinkRoute4* copy() const
      {
	NetlinkRoute4 *ret = new NetlinkRoute4;
	ret->route = route;
	ret->gw = gw;
	ret->dev = dev;
	return ret;
      }

      virtual void execute(std::ostream& os) override
      {
	int ret;

	if (dev.empty())
	{
	  os << "Error: can't call NetlinkRoute4 with no interface" << std::endl;
	  return;
	}

	if (add)
	{
	  ret = SITNL::net_route_add(route, gw, dev, 0, 0);
	}
	else
	{
	  ret = SITNL::net_route_del(route, gw, dev, 0, 0);
	}

	if (ret)
	{
	  os << "Error while executing NetlinkRoute4(add: " << add << ") "
	    << dev << ": " << ret << std::endl;
	}
      }

      virtual std::string to_string() const override
      {
	std::ostringstream os;
	os << "netlink route " << (add ? "add" : "del") << " dev " << dev << " "
	  << route << " via " << gw.to_string();
	return os.str();
      }

      IP::Route4 route;
      IPv4::Addr gw;
      std::string dev;
      bool add = true;
    };

    struct NetlinkRoute6 : public Action
    {
      typedef RCPtr<NetlinkRoute6> Ptr;

      NetlinkRoute6() {}

      NetlinkRoute6(IPv6::Addr& dst_arg, int prefixlen_arg, IPv6::Addr& gw_arg,
		    std::string dev_arg, bool add_arg)
	: route(dst_arg, prefixlen_arg),
	  gw(gw_arg),
	  dev(dev_arg),
	  add(add_arg)
      {
      }

      NetlinkRoute6* copy() const
      {
	NetlinkRoute6 *ret = new NetlinkRoute6;
	ret->route = route;
	ret->gw = gw;
	ret->dev = dev;
	return ret;
      }

      virtual void execute(std::ostream& os) override
      {
	int ret;

	if (dev.empty())
	{
	  os << "Error: can't call NetlinkRoute6 with no interface" << std::endl;
	  return;
	}

	if (add)
	{
	  ret = SITNL::net_route_add(route, gw, dev, 0, 0);
	}
	else
	{
	  ret = SITNL::net_route_del(route, gw, dev, 0, 0);
	}

	if (ret)
	{
	  os << "Error while executing NetlinkRoute6(add: " << add << ") "
	    << dev << ": " << ret << std::endl;
	}
      }

      virtual std::string to_string() const override
      {
	std::ostringstream os;
	os << "netlink route " << (add ? "add" : "del") << " dev " << dev << " "
	  << route << " via " << gw.to_string();
	return os.str();
      }

      IP::Route6 route;
      IPv6::Addr gw;
      std::string dev;
      bool add = true;
    };

    enum { // add_del_route flags
      R_IPv6=(1<<0),
      R_ADD_SYS=(1<<1),
      R_ADD_DCO=(1<<2),
      R_ADD_ALL=R_ADD_SYS|R_ADD_DCO,
    };

    /**
     * @brief Add new interface
     *
     * @param os output stream to where error message is written
     * @param dev interface name
     * @param type interface link type (such as "ovpn-dco")
     * @return int 0 on success, negative error code on error
     */
    inline int iface_new(std::ostringstream& os, const std::string& dev, const std::string& type)
    {
      int ret = -1;

      if (dev.empty())
      {
	os << "Error: can't call NetlinkLinkNew with no interface" << std::endl;
	return ret;
      }

      if (type.empty())
      {
	os << "Error: can't call NetlinkLinkNew with no interfacei type" << std::endl;
	return ret;
      }

      ret = SITNL::net_iface_new(dev, type);
      if (ret)
      {
	os << "Error while executing NetlinkLinkNew " << dev << ": " << ret << std::endl;
      }

      return ret;
    }

    inline int iface_del(std::ostringstream& os, const std::string& dev)
    {
      int ret = -1;

      if (dev.empty())
      {
	os << "Error: can't call NetlinkLinkDel with no interface" << std::endl;
	return ret;
      }

      ret = SITNL::net_iface_del(dev);
      if (ret)
      {
	os << "Error while executing NetlinkLinkDel " << dev << ": " << ret << std::endl;
      }

      return ret;
    }

    /*inline IPv4::Addr cvt_pnr_ip_v4(const std::string& hexaddr)
    {
      BufferAllocated v(4, BufferAllocated::CONSTRUCT_ZERO);
      parse_hex(v, hexaddr);
      if (v.size() != 4)
	throw tun_linux_error("bad hex address");
      IPv4::Addr ret = IPv4::Addr::from_bytes(v.data());
      return IP::Addr::from_ipv4(ret);
    }*/

    inline void add_del_route(const std::string& addr_str,
			      const int prefix_len,
			      const std::string& gateway_str,
			      const std::string& dev,
			      const unsigned int flags,
			      std::vector<IP::Route>* rtvec,
			      Action::Ptr& create,
			      Action::Ptr& destroy)
    {
      if (flags & R_IPv6)
	{
	  const IPv6::Addr addr = IPv6::Addr::from_string(addr_str);
	  const IPv6::Addr netmask = IPv6::Addr::netmask_from_prefix_len(prefix_len);
	  const IPv6::Addr net = addr & netmask;

	  if (flags & R_ADD_SYS)
	    {
	      // ip route add 2001:db8:1::/48 via 2001:db8:1::1
	      NetlinkRoute6::Ptr add(new NetlinkRoute6);
	      add->route.addr = net;
	      add->route.prefix_len = prefix_len;
	      add->gw = IPv6::Addr::from_string(gateway_str);
	      add->dev = dev;
	      add->add = true;

	      create = add;
	      // for the destroy command, copy the add command but replace "add" with "delete"
	      NetlinkRoute6::Ptr del(add->copy());
	      del->add = false;
	      destroy = del;
	    }

	  if (rtvec && (flags & R_ADD_DCO))
	    rtvec->emplace_back(IP::Addr::from_ipv6(net), prefix_len);
	}
      else
	{
	  const IPv4::Addr addr = IPv4::Addr::from_string(addr_str);
	  const IPv4::Addr netmask = IPv4::Addr::netmask_from_prefix_len(prefix_len);
	  const IPv4::Addr net = addr & netmask;

	  if (flags & R_ADD_SYS)
	    {
	      // ip route add 192.0.2.128/25 via 192.0.2.1
	      NetlinkRoute4::Ptr add(new NetlinkRoute4);
	      add->route.addr = net;
	      add->route.prefix_len = prefix_len;
	      add->gw = IPv4::Addr::from_string(gateway_str);
	      add->dev = dev;
	      add->add = true;

	      create = add;
	      // for the destroy command, copy the add command but replace "add" with "delete"
	      NetlinkRoute4::Ptr del(add->copy());
	      del->add = false;
	      destroy = del;
	    }

	  if (rtvec && (flags & R_ADD_DCO))
	    rtvec->emplace_back(IP::Addr::from_ipv4(net), prefix_len);
	}
    }

    inline void add_del_route(const std::string& addr_str,
			      const int prefix_len,
			      const std::string& gateway_str,
			      const std::string& dev,
			      const unsigned int flags,// add interface route to rtvec if defined
			      std::vector<IP::Route>* rtvec,
			      ActionList& create,
			      ActionList& destroy)
    {
      Action::Ptr c, d;
      add_del_route(addr_str, prefix_len, gateway_str, dev, flags, rtvec, c, d);
      create.add(c);
      destroy.add(d);
    }

    inline void iface_up(const std::string& iface_name,
			     const int mtu,
			     ActionList& create,
			     ActionList& destroy)
    {
      {
	NetlinkLinkSet::Ptr add(new NetlinkLinkSet);
	add->dev = iface_name;
	add->up = true;
	add->mtu = mtu;

	create.add(add);
	// for the destroy command, copy the add command but replace "up" with "down"
	NetlinkLinkSet::Ptr del(add->copy());
	del->up = false;
	destroy.add(del);
      }
    }

    inline void iface_config(const std::string& iface_name,
			     int unit,
			     const TunBuilderCapture& pull,
			     std::vector<IP::Route>* rtvec,
			     ActionList& create,
			     ActionList& destroy)
    {
      // set local4 and local6 to point to IPv4/6 route configurations
      const TunBuilderCapture::RouteAddress* local4 = pull.vpn_ipv4();
      const TunBuilderCapture::RouteAddress* local6 = pull.vpn_ipv6();

      // Set IPv4 Interface
      if (local4)
	{
	  NetlinkAddr4::Ptr add(new NetlinkAddr4);
	  add->addr = IPv4::Addr::from_string(local4->address);
	  add->prefixlen = local4->prefix_length;
	  add->broadcast = IPv4::Addr::from_string(local4->address)
	    | ~IPv4::Addr::netmask_from_prefix_len(local4->prefix_length);
	  add->dev = iface_name;
	  add->add = true;
//	  if (unit >= 0)
//	    {
//	      add->argv.push_back("label");
//	      add->argv.push_back(iface_name + ':' + openvpn::to_string(unit));
//	    }
	  create.add(add);

	  // for the destroy command, copy the add command but replace "add" with "delete"
	  NetlinkAddr4::Ptr del(add->copy());
	  del->add = false;
	  destroy.add(del);

	  // add interface route to rtvec if defined
	  add_del_route(local4->address, local4->prefix_length, local4->address, iface_name, R_ADD_DCO, rtvec, create, destroy);
	}

      // Set IPv6 Interface
      if (local6 && !pull.block_ipv6)
	{
	  NetlinkAddr6::Ptr add(new NetlinkAddr6);
	  add->addr = IPv6::Addr::from_string(local6->address);
	  add->prefixlen = local6->prefix_length;
	  add->dev = iface_name;
	  add->add = true;

	  create.add(add);

	  // for the destroy command, copy the add command but replace "add" with "delete"
	  NetlinkAddr6::Ptr del(add->copy());
	  del->add = false;
	  destroy.add(del);

	  // add interface route to rtvec if defined
	  add_del_route(local6->address, local6->prefix_length, local6->address, iface_name, R_ADD_DCO|R_IPv6, rtvec, create, destroy);
	}
    }

    struct TunMethods
    {
      static inline void tun_config(const std::string& iface_name,
				    const TunBuilderCapture& pull,
				    std::vector<IP::Route>* rtvec,
				    ActionList& create,
				    ActionList& destroy,
				    const unsigned int flags) // TunConfigFlags
      {
	// set local4 and local6 to point to IPv4/6 route configurations
	const TunBuilderCapture::RouteAddress* local4 = pull.vpn_ipv4();
	const TunBuilderCapture::RouteAddress* local6 = pull.vpn_ipv6();

	// configure interface
	if (!(flags & TunConfigFlags::DISABLE_IFACE_UP))
	  iface_up(iface_name, pull.mtu, create, destroy);
	iface_config(iface_name, -1, pull, rtvec, create, destroy);

	// Process Routes
	{
	  for (const auto &route : pull.add_routes)
	    {
	      if (route.ipv6)
		{
		  if (local6 && !pull.block_ipv6)
		    add_del_route(route.address, route.prefix_length, local6->gateway, iface_name, R_ADD_ALL|R_IPv6, rtvec, create, destroy);
		}
	      else
		{
		  if (local4 && !local4->gateway.empty())
		    add_del_route(route.address, route.prefix_length, local4->gateway, iface_name, R_ADD_ALL, rtvec, create, destroy);
		  else
		    OPENVPN_LOG("ERROR: IPv4 route pushed without IPv4 ifconfig and/or route-gateway");
		}
	    }
	}

	// Process exclude routes
	if (!pull.exclude_routes.empty())
	{
	  LinuxGW46Netlink gw(iface_name);

	  for (const auto &route : pull.exclude_routes)
	    {
	      if (route.ipv6)
		{
		  OPENVPN_LOG("NOTE: exclude IPv6 routes not supported yet"); // fixme
		}
	      else
		{
		  if (gw.v4.defined())
		    add_del_route(route.address, route.prefix_length, gw.v4.addr().to_string(), gw.v4.dev(), R_ADD_SYS, rtvec, create, destroy);
		  else
		    OPENVPN_LOG("NOTE: cannot determine gateway for exclude IPv4 routes");
		}
	    }
	}

	// Process IPv4 redirect-gateway
	if (!(flags & TunConfigFlags::DISABLE_REROUTE_GW))
	  {
	    if (pull.reroute_gw.ipv4 && local4)
	      {
		// add bypass route
		if ((flags & TunConfigFlags::ADD_BYPASS_ROUTES) && !pull.remote_address.ipv6 && !(pull.reroute_gw.flags & RedirectGatewayFlags::RG_LOCAL))
		  add_bypass_route(iface_name, pull.remote_address.address, false, rtvec, create, destroy);

		add_del_route("0.0.0.0", 1, local4->gateway, iface_name, R_ADD_ALL, rtvec, create, destroy);
		add_del_route("128.0.0.0", 1, local4->gateway, iface_name, R_ADD_ALL, rtvec, create, destroy);
	      }

	    // Process IPv6 redirect-gateway
	    if (pull.reroute_gw.ipv6 && !pull.block_ipv6 && local6)
	      {
		// add bypass route
		if ((flags & TunConfigFlags::ADD_BYPASS_ROUTES) && pull.remote_address.ipv6 && !(pull.reroute_gw.flags & RedirectGatewayFlags::RG_LOCAL))
		  add_bypass_route(iface_name, pull.remote_address.address, true, rtvec, create, destroy);

		add_del_route("0000::", 1, local6->gateway, iface_name, R_ADD_ALL|R_IPv6, rtvec, create, destroy);
		add_del_route("8000::", 1, local6->gateway, iface_name, R_ADD_ALL|R_IPv6, rtvec, create, destroy);
	      }
	  }

	// fixme -- Process block-ipv6

	// fixme -- Handle pushed DNS servers
      }

      static inline void add_bypass_route(const std::string& tun_iface_name,
					  const std::string& address,
					  bool ipv6,
					  std::vector<IP::Route>* rtvec,
					  ActionList& create,
					  ActionList& destroy)
      {
	LinuxGW46Netlink gw(tun_iface_name, address);

	if (!ipv6 && gw.v4.defined())
	  add_del_route(address, 32, gw.v4.addr().to_string(), gw.dev(), R_ADD_SYS, rtvec, create, destroy);

	if (ipv6 && gw.v6.defined())
	  add_del_route(address, 128, gw.v6.addr().to_string(), gw.dev(), R_IPv6|R_ADD_SYS, rtvec, create, destroy);
      }
    };
  }
} // namespace openvpn
