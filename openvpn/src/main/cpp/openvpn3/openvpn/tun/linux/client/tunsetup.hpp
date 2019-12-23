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

// Client tun interface for Linux.

#ifndef OPENVPN_TUN_LINUX_CLIENT_TUNSETUP_H
#define OPENVPN_TUN_LINUX_CLIENT_TUNSETUP_H

#include <sys/ioctl.h>
#include <fcntl.h>
#include <errno.h>
#include <net/if.h>
#include <linux/if_tun.h>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/file.hpp>
#include <openvpn/common/split.hpp>
#include <openvpn/common/splitlines.hpp>
#include <openvpn/common/hexstr.hpp>
#include <openvpn/common/to_string.hpp>
#include <openvpn/common/process.hpp>
#include <openvpn/common/action.hpp>
#include <openvpn/addr/route.hpp>
#include <openvpn/tun/builder/capture.hpp>
#include <openvpn/tun/builder/setup.hpp>
#include <openvpn/tun/client/tunbase.hpp>
#include <openvpn/tun/client/tunprop.hpp>
#include <openvpn/netconf/linux/gw.hpp>

namespace openvpn {
  namespace TunLinux {

    OPENVPN_EXCEPTION(tun_linux_error);
    OPENVPN_EXCEPTION(tun_open_error);
    OPENVPN_EXCEPTION(tun_layer_error);
    OPENVPN_EXCEPTION(tun_ioctl_error);
    OPENVPN_EXCEPTION(tun_fcntl_error);
    OPENVPN_EXCEPTION(tun_name_error);
    OPENVPN_EXCEPTION(tun_tx_queue_len_error);
    OPENVPN_EXCEPTION(tun_ifconfig_error);

    enum { // add_del_route flags
      R_IPv6=(1<<0),
      R_ADD_SYS=(1<<1),
      R_ADD_DCO=(1<<2),
      R_ADD_ALL=R_ADD_SYS|R_ADD_DCO,
    };

    inline IP::Addr cvt_pnr_ip_v4(const std::string& hexaddr)
    {
      BufferAllocated v(4, BufferAllocated::CONSTRUCT_ZERO);
      parse_hex(v, hexaddr);
      if (v.size() != 4)
	throw tun_linux_error("bad hex address");
      IPv4::Addr ret = IPv4::Addr::from_bytes(v.data());
      return IP::Addr::from_ipv4(ret);
    }

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
	      Command::Ptr add(new Command);
	      add->argv.push_back("/sbin/ip");
	      add->argv.push_back("-6");
	      add->argv.push_back("route");
	      add->argv.push_back("add");
	      add->argv.push_back(net.to_string() + '/' + openvpn::to_string(prefix_len));
	      add->argv.push_back("via");
	      add->argv.push_back(gateway_str);
	      if (!dev.empty())
		{
		  add->argv.push_back("dev");
		  add->argv.push_back(dev);
		}
	      create = add;

	      // for the destroy command, copy the add command but replace "add" with "delete"
	      Command::Ptr del(add->copy());
	      del->argv[3] = "del";
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
	      Command::Ptr add(new Command);
	      add->argv.push_back("/sbin/ip");
	      add->argv.push_back("-4");
	      add->argv.push_back("route");
	      add->argv.push_back("add");
	      add->argv.push_back(net.to_string() + '/' + openvpn::to_string(prefix_len));
	      add->argv.push_back("via");
	      add->argv.push_back(gateway_str);
	      create = add;

	      // for the destroy command, copy the add command but replace "add" with "delete"
	      Command::Ptr del(add->copy());
	      del->argv[3] = "del";
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
	Command::Ptr add(new Command);
	add->argv.push_back("/sbin/ip");
	add->argv.push_back("link");
	add->argv.push_back("set");
	add->argv.push_back(iface_name);
	add->argv.push_back("up");
	if (mtu > 0)
	  {
	    add->argv.push_back("mtu");
	    add->argv.push_back(openvpn::to_string(mtu));
	  }
	create.add(add);

	// for the destroy command, copy the add command but replace "up" with "down"
	Command::Ptr del(add->copy());
	del->argv[4] = "down";
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
	  Command::Ptr add(new Command);
	  add->argv.push_back("/sbin/ip");
	  add->argv.push_back("-4");
	  add->argv.push_back("addr");
	  add->argv.push_back("add");
	  add->argv.push_back(local4->address + '/' + openvpn::to_string(local4->prefix_length));
	  add->argv.push_back("broadcast");
	  add->argv.push_back((IPv4::Addr::from_string(local4->address) | ~IPv4::Addr::netmask_from_prefix_len(local4->prefix_length)).to_string());
	  add->argv.push_back("dev");
	  add->argv.push_back(iface_name);
	  if (unit >= 0)
	    {
	      add->argv.push_back("label");
	      add->argv.push_back(iface_name + ':' + openvpn::to_string(unit));
	    }
	  create.add(add);

	  // for the destroy command, copy the add command but replace "add" with "delete"
	  Command::Ptr del(add->copy());
	  del->argv[3] = "del";
	  destroy.add(del);

	  // add interface route to rtvec if defined
	  add_del_route(local4->address, local4->prefix_length, local4->address, iface_name, R_ADD_DCO, rtvec, create, destroy);
	}

      // Set IPv6 Interface
      if (local6 && !pull.block_ipv6)
	{
	  Command::Ptr add(new Command);
	  add->argv.push_back("/sbin/ip");
	  add->argv.push_back("-6");
	  add->argv.push_back("addr");
	  add->argv.push_back("add");
	  add->argv.push_back(local6->address + '/' + openvpn::to_string(local6->prefix_length));
	  add->argv.push_back("dev");
	  add->argv.push_back(iface_name);
	  create.add(add);

	  // for the destroy command, copy the add command but replace "add" with "delete"
	  Command::Ptr del(add->copy());
	  del->argv[3] = "del";
	  destroy.add(del);

	  // add interface route to rtvec if defined
	  add_del_route(local6->address, local6->prefix_length, local6->address, iface_name, R_ADD_DCO|R_IPv6, rtvec, create, destroy);
	}
    }

    inline void tun_config(const std::string& iface_name,
			   const TunBuilderCapture& pull,
			   std::vector<IP::Route>* rtvec,
			   ActionList& create,
			   ActionList& destroy)
    {
      const LinuxGW46 gw(true);

      // set local4 and local6 to point to IPv4/6 route configurations
      const TunBuilderCapture::RouteAddress* local4 = pull.vpn_ipv4();
      const TunBuilderCapture::RouteAddress* local6 = pull.vpn_ipv6();

      // configure interface
      iface_up(iface_name, pull.mtu, create, destroy);
      iface_config(iface_name, -1, pull, rtvec, create, destroy);

      // Process Routes
      {
	for (const auto &route : pull.add_routes)
	  {
	    if (route.ipv6)
	      {
		if (!pull.block_ipv6)
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
      {
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
      if (pull.reroute_gw.ipv4)
	{
	  // add bypass route
	  if (!pull.remote_address.ipv6 && !(pull.reroute_gw.flags & RedirectGatewayFlags::RG_LOCAL))
	    add_del_route(pull.remote_address.address, 32, gw.v4.addr().to_string(), gw.v4.dev(), R_ADD_SYS, rtvec, create, destroy);

	  add_del_route("0.0.0.0", 1, local4->gateway, iface_name, R_ADD_ALL, rtvec, create, destroy);
	  add_del_route("128.0.0.0", 1, local4->gateway, iface_name, R_ADD_ALL, rtvec, create, destroy);
	}

      // Process IPv6 redirect-gateway
      if (pull.reroute_gw.ipv6 && !pull.block_ipv6)
	{
	  // add bypass route
	  if (pull.remote_address.ipv6 && !(pull.reroute_gw.flags & RedirectGatewayFlags::RG_LOCAL))
	    add_del_route(pull.remote_address.address, 128, gw.v6.addr().to_string(), gw.v6.dev(), R_ADD_SYS|R_IPv6, rtvec, create, destroy);

	  add_del_route("0000::", 1, local6->gateway, iface_name, R_ADD_ALL|R_IPv6, rtvec, create, destroy);
	  add_del_route("8000::", 1, local6->gateway, iface_name, R_ADD_ALL|R_IPv6, rtvec, create, destroy);
	}

      // fixme -- Process block-ipv6

      // fixme -- Handle pushed DNS servers
    }

    class Setup : public TunBuilderSetup::Base
    {
    public:
      typedef RCPtr<Setup> Ptr;

      struct Config : public TunBuilderSetup::Config
      {
	std::string iface_name;
	Layer layer; // OSI layer
	std::string dev_name;
	int txqueuelen;

#ifdef HAVE_JSON
	virtual Json::Value to_json() override
	{
	  Json::Value root(Json::objectValue);
	  root["iface_name"] = Json::Value(iface_name);
	  root["layer"] = Json::Value(layer.str());
	  root["dev_name"] = Json::Value(dev_name);
	  root["txqueuelen"] = Json::Value(txqueuelen);
	  return root;
	};

	virtual void from_json(const Json::Value& root, const std::string& title) override
	{
	  json::assert_dict(root, title);
	  json::to_string(root, iface_name, "iface_name", title);
	  layer = Layer::from_str(json::get_string(root, "layer", title));
	  json::to_string(root, dev_name, "dev_name", title);
	  json::to_int(root, txqueuelen, "txqueuelen", title);
	}
#endif
      };

      virtual void destroy(std::ostream &os) override
      {
	// remove added routes
	if (remove_cmds)
	  remove_cmds->execute(std::cout);
      }

      virtual int establish(const TunBuilderCapture& pull, // defined by TunBuilderSetup::Base
			    TunBuilderSetup::Config* config,
			    Stop* stop,
			    std::ostream& os) override
      {
	// get configuration
	Config *conf = dynamic_cast<Config *>(config);
	if (!conf)
	  throw tun_linux_error("missing config");

	static const char node[] = "/dev/net/tun";
	ScopedFD fd(open(node, O_RDWR));
	if (!fd.defined())
	  OPENVPN_THROW(tun_open_error, "error opening tun device " << node << ": " << errinfo(errno));

	struct ifreq ifr;
	std::memset(&ifr, 0, sizeof(ifr));
	ifr.ifr_flags = IFF_ONE_QUEUE;
	ifr.ifr_flags |= IFF_NO_PI;
	if (conf->layer() == Layer::OSI_LAYER_3)
	  ifr.ifr_flags |= IFF_TUN;
	else if (conf->layer() == Layer::OSI_LAYER_2)
	  ifr.ifr_flags |= IFF_TAP;
	else
	  throw tun_layer_error("unknown OSI layer");

	open_unit(conf->dev_name, ifr, fd);

	if (fcntl (fd(), F_SETFL, O_NONBLOCK) < 0)
	  throw tun_fcntl_error(errinfo(errno));

	// Set the TX send queue size
	if (conf->txqueuelen)
	  {
	    struct ifreq netifr;
	    ScopedFD ctl_fd(socket (AF_INET, SOCK_DGRAM, 0));

	    if (ctl_fd.defined())
	      {
		std::memset(&netifr, 0, sizeof(netifr));
		strcpy (netifr.ifr_name, ifr.ifr_name);
		netifr.ifr_qlen = conf->txqueuelen;
		if (ioctl (ctl_fd(), SIOCSIFTXQLEN, (void *) &netifr) < 0)
		  throw tun_tx_queue_len_error(errinfo(errno));
	      }
	    else
	      throw tun_tx_queue_len_error(errinfo(errno));
	  }

	conf->iface_name = ifr.ifr_name;

	ActionList::Ptr add_cmds = new ActionList();
	remove_cmds.reset(new ActionListReversed()); // remove commands executed in reversed order

	// configure tun properties
	tun_config(ifr.ifr_name, pull, nullptr, *add_cmds, *remove_cmds);

	// execute commands to bring up interface
	add_cmds->execute(std::cout);

	return fd.release();
      }

    private:
      void open_unit(const std::string& name, struct ifreq& ifr, ScopedFD& fd)
      {
	if (!name.empty())
	  {
	    const int max_units = 256;
	    for (int unit = 0; unit < max_units; ++unit)
	      {
		std::string n = name;
		if (unit)
		  n += openvpn::to_string(unit);
		if (n.length() < IFNAMSIZ)
		  ::strcpy (ifr.ifr_name, n.c_str());
		else
		  throw tun_name_error();
		if (ioctl (fd(), TUNSETIFF, (void *) &ifr) == 0)
		  return;
	      }
	    const int eno = errno;
	    OPENVPN_THROW(tun_ioctl_error, "failed to open tun device '" << name << "' after trying " << max_units << " units : " << errinfo(eno));
	  }
	else
	  {
	    if (ioctl (fd(), TUNSETIFF, (void *) &ifr) < 0)
	      {
		const int eno = errno;
		OPENVPN_THROW(tun_ioctl_error, "failed to open tun device '" << name << "' : " << errinfo(eno));
	      }
	  }
      }

      ActionListReversed::Ptr remove_cmds;
    };
  }
} // namespace openvpn

#endif // OPENVPN_TUN_LINUX_CLIENT_TUNCLI_H
