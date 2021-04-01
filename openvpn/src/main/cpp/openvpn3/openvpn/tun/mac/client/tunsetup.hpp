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

// Client tun setup for Mac

#ifndef OPENVPN_TUN_MAC_CLIENT_TUNSETUP_H
#define OPENVPN_TUN_MAC_CLIENT_TUNSETUP_H

#include <string>
#include <sstream>
#include <ostream>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/common/size.hpp>
#include <openvpn/common/arraysize.hpp>
#include <openvpn/common/action.hpp>
#include <openvpn/common/process.hpp>
#include <openvpn/common/jsonlib.hpp>
#include <openvpn/error/excode.hpp>
#include <openvpn/tun/layer.hpp>
#include <openvpn/tun/mac/tunutil.hpp>
#include <openvpn/tun/mac/utun.hpp>
#include <openvpn/tun/mac/macgw.hpp>
#include <openvpn/tun/mac/macdns_watchdog.hpp>
#include <openvpn/tun/proxy.hpp>
#include <openvpn/tun/mac/macproxy.hpp>
#include <openvpn/tun/builder/rgwflags.hpp>
#include <openvpn/tun/builder/setup.hpp>

#ifdef HAVE_JSON
#include <openvpn/common/jsonhelper.hpp>
#endif

namespace openvpn {
  namespace TunMac {
    class Setup : public TunBuilderSetup::Base
    {
    public:
      typedef RCPtr<Setup> Ptr;

      OPENVPN_EXCEPTION(tun_mac_setup);

      struct Config : public TunBuilderSetup::Config
      {
	std::string iface_name;
	Layer layer;               // OSI layer
	bool tun_prefix = false;
	bool add_bypass_routes_on_establish = false;

#ifdef HAVE_JSON
	virtual Json::Value to_json() override
	{
	  Json::Value root(Json::objectValue);
	  root["iface_name"] = Json::Value(iface_name);
	  root["layer"] = Json::Value(layer.str());
	  root["tun_prefix"] = Json::Value(tun_prefix);
	  return root;
	};

	virtual void from_json(const Json::Value& root, const std::string& title) override
	{
	  json::assert_dict(root, title);
	  json::to_string(root, iface_name, "iface_name", title);
	  layer = Layer::from_str(json::get_string(root, "layer", title));
	  json::to_bool(root, tun_prefix, "tun_prefix", title);
	}
#endif
      };

      bool add_bypass_route(const std::string& address,
			    bool ipv6,
			    std::ostream& os)
      {
        // not yet implemented
        return true;
      }

      virtual int establish(const TunBuilderCapture& pull, // defined by TunBuilderSetup::Base
			    TunBuilderSetup::Config* config,
			    Stop* stop,
			    std::ostream& os) override
      {
	// get configuration
	Config *conf = dynamic_cast<Config *>(config);
	if (!conf)
	  throw tun_mac_setup("missing config");

	// close out old remove cmds, if they exist
	destroy(os);

	// Open tun device.  Try Mac OS X integrated utun device first
	// (layer 3 only).  If utun fails and MAC_TUNTAP_FALLBACK is defined,
	// then fall back to TunTap third-party device.
	// If successful, conf->iface_name will be set to tun iface name.
	int fd = -1;
	conf->tun_prefix = false;
	try {
#         if defined(MAC_TUNTAP_FALLBACK)
#           if !defined(ASIO_DISABLE_KQUEUE)
#             error Mac OS X TunTap adapter is incompatible with kqueue; rebuild with ASIO_DISABLE_KQUEUE
#           endif
	    if (conf->layer() == Layer::OSI_LAYER_3)
	      {
	        try {
		  fd = UTun::utun_open(conf->iface_name);
		  conf->tun_prefix = true;
	        }
	        catch (const std::exception& e)
		  {
		    os << e.what() << std::endl;
		  }
	      }
	    if (fd == -1)
	      fd = Util::tuntap_open(conf->layer, conf->iface_name);
#         else
	    fd = UTun::utun_open(conf->iface_name);
	    conf->tun_prefix = true;
#         endif
	}
	catch (const std::exception& e)
	  {
	    throw ErrorCode(Error::TUN_IFACE_CREATE, true, e.what());
	  }

	// create ActionLists for setting up and removing adapter properties
	ActionList::Ptr add_cmds(new ActionList());
	remove_cmds.reset(new ActionList());

	// populate add/remove lists with actions
	tun_config(conf->iface_name, pull, *add_cmds, *remove_cmds, os);

	// execute the add actions
	add_cmds->execute(os);

	// now that the add actions have succeeded,
	// enable the remove actions
	remove_cmds->enable_destroy(true);

	os << "open " << conf->iface_name << " SUCCEEDED" << std::endl;
	return fd;
      }

      virtual void destroy(std::ostream& os) override // defined by DestructorBase
      {
	if (remove_cmds)
	  {
	    remove_cmds->destroy(os);
	    remove_cmds.reset();
	  }
      }

      virtual ~Setup()
      {
	std::ostringstream os;
	destroy(os);
      }

    private:
      enum { // add_del_route flags
	R_IPv6=(1<<0),
	R_IFACE=(1<<1),
	R_IFACE_HINT=(1<<2),
	R_ONLINK=(1<<3),
	R_REJECT=(1<<4),
	R_BLACKHOLE=(1<<5),
      };

      static void add_del_route(const std::string& addr_str,
				const int prefix_len,
				const std::string& gateway_str,
				const std::string& iface,
				const unsigned int flags,
				Action::Ptr& create,
				Action::Ptr& destroy)
      {
	if (flags & R_IPv6)
	  {
	    const IPv6::Addr addr = IPv6::Addr::from_string(addr_str);
	    const IPv6::Addr netmask = IPv6::Addr::netmask_from_prefix_len(prefix_len);
	    const IPv6::Addr net = addr & netmask;

	    Command::Ptr add(new Command);
	    add->argv.push_back("/sbin/route");
	    add->argv.push_back("add");
	    add->argv.push_back("-net");
	    add->argv.push_back("-inet6");
	    add->argv.push_back(net.to_string());
	    add->argv.push_back("-prefixlen");
	    add->argv.push_back(to_string(prefix_len));
	    if (flags & R_REJECT)
	      add->argv.push_back("-reject");
	    if (flags & R_BLACKHOLE)
	      add->argv.push_back("-blackhole");
	    if (!iface.empty())
	      {
		if (flags & R_IFACE)
		  {
		    add->argv.push_back("-iface");
		    add->argv.push_back(iface);
		  }
	      }
	    if (!gateway_str.empty() && !(flags & R_IFACE))
	      {
		std::string g = gateway_str;
		if (flags & R_IFACE_HINT)
		  g += '%' + iface;
		add->argv.push_back(g);
	      }
	    create = add;

	    // for the destroy command, copy the add command but replace "add" with "delete"
	    Command::Ptr del(add->copy());
	    del->argv[1] = "delete";
	    destroy = del;
	  }
	else
	  {
	    const IPv4::Addr addr = IPv4::Addr::from_string(addr_str);
	    const IPv4::Addr netmask = IPv4::Addr::netmask_from_prefix_len(prefix_len);
	    const IPv4::Addr net = addr & netmask;

	    Command::Ptr add(new Command);
	    add->argv.push_back("/sbin/route");
	    add->argv.push_back("add");
	    if (flags & R_ONLINK)
	      {
		add->argv.push_back("-cloning");
		add->argv.push_back("-net");
		add->argv.push_back(net.to_string());
		add->argv.push_back("-netmask");
		add->argv.push_back(netmask.to_string());
		add->argv.push_back("-interface");
		add->argv.push_back(iface);
	      }
	    else
	      {
		add->argv.push_back("-net");
		add->argv.push_back(net.to_string());
		add->argv.push_back("-netmask");
		add->argv.push_back(netmask.to_string());
		if (flags & R_REJECT)
		  add->argv.push_back("-reject");
		if (flags & R_BLACKHOLE)
		  add->argv.push_back("-blackhole");
		if (!iface.empty())
		  {
		    if (flags & R_IFACE)
		      {
			add->argv.push_back("-iface");
			add->argv.push_back(iface);
		      }
		  }
		add->argv.push_back(gateway_str);
	      }
	    create = add;

	    // for the destroy command, copy the add command but replace "add" with "delete"
	    Command::Ptr del(add->copy());
	    del->argv[1] = "delete";
	    destroy = del;
	  }
      }

      static void add_del_route(const std::string& addr_str,
				const int prefix_len,
				const std::string& gateway_str,
				const std::string& iface,
				const unsigned int flags,
				ActionList& create,
				ActionList& destroy)
      {
	Action::Ptr c, d;
	add_del_route(addr_str, prefix_len, gateway_str, iface, flags, c, d);
	create.add(c);
	destroy.add(d);
      }

      static void tun_config(const std::string& iface_name,
			     const TunBuilderCapture& pull,
			     ActionList& create,
			     ActionList& destroy,
			     std::ostream& os)
      {
	// get default gateway
	MacGWInfo gw;

	// set local4 and local6 to point to IPv4/6 route configurations
	const TunBuilderCapture::RouteAddress* local4 = nullptr;
	const TunBuilderCapture::RouteAddress* local6 = nullptr;
	if (pull.tunnel_address_index_ipv4 >= 0)
	  local4 = &pull.tunnel_addresses[pull.tunnel_address_index_ipv4];
	if (pull.tunnel_address_index_ipv6 >= 0)
	  local6 = &pull.tunnel_addresses[pull.tunnel_address_index_ipv6];

	// Interface down
	Command::Ptr iface_down(new Command);
	iface_down->argv.push_back("/sbin/ifconfig");
	iface_down->argv.push_back(iface_name);
	iface_down->argv.push_back("down");
	create.add(iface_down);

	// Set IPv4 Interface
	if (local4)
	  {
	    // Process ifconfig
	    const IPv4::Addr netmask = IPv4::Addr::netmask_from_prefix_len(local4->prefix_length);
	    {
	      Command::Ptr cmd(new Command);
	      cmd->argv.push_back("/sbin/ifconfig");
	      cmd->argv.push_back(iface_name);
	      cmd->argv.push_back(local4->address);
	      cmd->argv.push_back(local4->gateway);
	      cmd->argv.push_back("netmask");
	      cmd->argv.push_back(netmask.to_string());
	      cmd->argv.push_back("mtu");
	      cmd->argv.push_back(to_string(pull.mtu));
	      cmd->argv.push_back("up");
	      create.add(cmd);
	    }
	    add_del_route(local4->address, local4->prefix_length, local4->address, iface_name, 0, create, destroy);
	  }

	// Set IPv6 Interface
	if (local6 && !pull.block_ipv6)
	  {
	    {
	      Command::Ptr cmd(new Command);
	      cmd->argv.push_back("/sbin/ifconfig");
	      cmd->argv.push_back(iface_name);
	      cmd->argv.push_back("inet6");
	      cmd->argv.push_back(local6->address + '/' + to_string(local6->prefix_length));
	      cmd->argv.push_back("up");
	      create.add(cmd);
	    }
	    add_del_route(local6->address, local6->prefix_length, "", iface_name, R_IPv6|R_IFACE, create, destroy);
	  }

	// Process Routes
	{
	  for (std::vector<TunBuilderCapture::Route>::const_iterator i = pull.add_routes.begin(); i != pull.add_routes.end(); ++i)
	    {
	      const TunBuilderCapture::Route& route = *i;
	      if (route.ipv6)
		{
		  if (!pull.block_ipv6)
		    add_del_route(route.address, route.prefix_length, local6->gateway, iface_name, R_IPv6|R_IFACE, create, destroy);
		}
	      else
		{
		  if (local4 && !local4->gateway.empty())
		    add_del_route(route.address, route.prefix_length, local4->gateway, iface_name, 0, create, destroy);
		  else
		    os << "ERROR: IPv4 route pushed without IPv4 ifconfig and/or route-gateway" << std::endl;
		}
	    }
	}

	// Process exclude routes
	if (!pull.exclude_routes.empty())
	  {
	    for (std::vector<TunBuilderCapture::Route>::const_iterator i = pull.exclude_routes.begin(); i != pull.exclude_routes.end(); ++i)
	      {
		const TunBuilderCapture::Route& route = *i;
		if (route.ipv6)
		  {
		    if (!pull.block_ipv6)
		      {
			if (gw.v6.defined())
			  add_del_route(route.address, route.prefix_length, gw.v6.router.to_string(), gw.v6.iface, R_IPv6|R_IFACE_HINT, create, destroy);
			else
			  os << "NOTE: cannot determine gateway for exclude IPv6 routes" << std::endl;
		      }
		  }
		else
		  {
		    if (gw.v4.defined())
		      add_del_route(route.address, route.prefix_length, gw.v4.router.to_string(), gw.v4.iface, 0, create, destroy);
		    else
		      os << "NOTE: cannot determine gateway for exclude IPv4 routes" << std::endl;
		  }
	      }
	  }

	// Process IPv4 redirect-gateway
	if (pull.reroute_gw.ipv4)
	  {
	    // add server bypass route
	    if (gw.v4.defined())
	      {
		if (!pull.remote_address.ipv6 && !(pull.reroute_gw.flags & RedirectGatewayFlags::RG_LOCAL))
		  {
		    Action::Ptr c, d;
		    add_del_route(pull.remote_address.address, 32, gw.v4.router.to_string(), gw.v4.iface, 0, c, d);
		    create.add(c);
		    destroy.add(d);
		    //add_del_route(gw.v4.router.to_string(), 32, "", gw.v4.iface, R_ONLINK, create, destroy); // fixme -- needed for block-local
		  }
	      }
	    else
	      os << "ERROR: cannot detect IPv4 default gateway" << std::endl;

	    if (!(pull.reroute_gw.flags & RGWFlags::EmulateExcludeRoutes))
	      {
		add_del_route("0.0.0.0", 1, local4->gateway, iface_name, 0, create, destroy);
		add_del_route("128.0.0.0", 1, local4->gateway, iface_name, 0, create, destroy);
	      }
	  }

	// Process IPv6 redirect-gateway
	if (pull.reroute_gw.ipv6 && !pull.block_ipv6)
	  {
	    // add server bypass route
	    if (gw.v6.defined())
	      {
		if (pull.remote_address.ipv6 && !(pull.reroute_gw.flags & RedirectGatewayFlags::RG_LOCAL))
		  {
		    Action::Ptr c, d;
		    add_del_route(pull.remote_address.address, 128, gw.v6.router.to_string(), gw.v6.iface, R_IPv6|R_IFACE_HINT, c, d);
		    create.add(c);
		    destroy.add(d);
		    //add_del_route(gw.v6.router.to_string(), 128, "", gw.v6.iface, R_IPv6|R_ONLINK, create, destroy); // fixme -- needed for block-local
		  }
	      }
	    else
	      os << "ERROR: cannot detect IPv6 default gateway" << std::endl;

	    if (!(pull.reroute_gw.flags & RGWFlags::EmulateExcludeRoutes))
	      {
		add_del_route("0000::", 1, local6->gateway, iface_name, R_IPv6|R_IFACE, create, destroy);
		add_del_route("8000::", 1, local6->gateway, iface_name, R_IPv6|R_IFACE, create, destroy);
	      }
	  }

	// Process block-ipv6
	if (pull.block_ipv6)
	  {
	    add_del_route("2000::", 4, "::1", "lo0", R_IPv6|R_REJECT|R_IFACE_HINT, create, destroy);
	    add_del_route("3000::", 4, "::1", "lo0", R_IPv6|R_REJECT|R_IFACE_HINT, create, destroy);
	    add_del_route("fc00::", 7, "::1", "lo0", R_IPv6|R_REJECT|R_IFACE_HINT, create, destroy);
	  }

	// Interface down
	destroy.add(iface_down);

	// configure DNS
	{
	  MacDNS::Config::Ptr dns(new MacDNS::Config(pull));
	  MacDNSWatchdog::add_actions(dns,
				      MacDNSWatchdog::FLUSH_RECONFIG
#ifdef ENABLE_DNS_WATCHDOG
				      | MacDNSWatchdog::SYNCHRONOUS
				      | MacDNSWatchdog::ENABLE_WATCHDOG
#endif
				      ,
				      create,
				      destroy);
	}
	
	if (pull.proxy_auto_config_url.defined())
	  ProxySettings::add_actions<MacProxySettings>(pull, create, destroy);
      }

      ActionList::Ptr remove_cmds;

    public:
      static void add_bypass_route(const std::string& route,
				   bool ipv6,
				   ActionList& add_cmds,
				   ActionList& remove_cmds_bypass_gw)
      {
      	MacGWInfo gw;

      	if (!ipv6)
	  {
	    if (gw.v4.defined())
	      add_del_route(route, 32, gw.v4.router.to_string(), gw.v4.iface, 0, add_cmds, remove_cmds_bypass_gw);
	  }
	else
	  {
	    if (gw.v6.defined())
	      add_del_route(route, 128, gw.v6.router.to_string(), gw.v6.iface, R_IPv6|R_IFACE_HINT, add_cmds, remove_cmds_bypass_gw);
	  }
      }
    };
  }
}

#endif
