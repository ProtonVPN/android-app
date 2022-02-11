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
#include <openvpn/asio/asioerr.hpp>
#include <openvpn/tun/builder/capture.hpp>
#include <openvpn/tun/builder/setup.hpp>
#include <openvpn/tun/client/tunbase.hpp>
#include <openvpn/tun/client/tunprop.hpp>
#include <openvpn/tun/client/tunconfigflags.hpp>
#include <openvpn/netconf/linux/gw.hpp>

namespace openvpn {
  namespace TunLinuxSetup {

    OPENVPN_EXCEPTION(tun_linux_error);
    OPENVPN_EXCEPTION(tun_open_error);
    OPENVPN_EXCEPTION(tun_layer_error);
    OPENVPN_EXCEPTION(tun_ioctl_error);
    OPENVPN_EXCEPTION(tun_fcntl_error);
    OPENVPN_EXCEPTION(tun_name_error);
    OPENVPN_EXCEPTION(tun_tx_queue_len_error);
    OPENVPN_EXCEPTION(tun_ifconfig_error);

    template <class TUNMETHODS>
    class Setup : public TunBuilderSetup::Base
    {
    public:
      typedef RCPtr<Setup> Ptr;

      // This empty constructor shouldn't be needed, but due to a
      // plausible compiler bug in GCC 4.8.5 (RHEL 7), this empty
      // constructor is required to be able to build.  This is
      // related to the member initialization of the private
      // remove_cmds_bypass_gw and remove_cmds class members.
      Setup() {}

      struct Config : public TunBuilderSetup::Config
      {
	std::string iface_name;
	Layer layer; // OSI layer
	std::string dev_name;
	int txqueuelen;
	bool add_bypass_routes_on_establish; // required when not using tunbuilder
	bool dco = false;

#ifdef HAVE_JSON
	virtual Json::Value to_json() override
	{
	  Json::Value root(Json::objectValue);
	  root["iface_name"] = Json::Value(iface_name);
	  root["layer"] = Json::Value(layer.str());
	  root["dev_name"] = Json::Value(dev_name);
	  root["txqueuelen"] = Json::Value(txqueuelen);
	  root["dco"] = Json::Value(dco);
	  return root;
	};

	virtual void from_json(const Json::Value& root, const std::string& title) override
	{
	  json::assert_dict(root, title);
	  json::to_string(root, iface_name, "iface_name", title);
	  layer = Layer::from_str(json::get_string(root, "layer", title));
	  json::to_string(root, dev_name, "dev_name", title);
	  json::to_int(root, txqueuelen, "txqueuelen", title);
	  json::to_bool(root, dco, "dco", title);
	}
#endif
      };

      void destroy(std::ostream &os) override
      {
	// remove added routes
	remove_cmds->execute(os);

	// remove bypass route
	remove_cmds_bypass_gw->execute(os);
      }

      bool add_bypass_route(const std::string& address,
			    bool ipv6,
			    std::ostream& os)
      {
	// nothing to do if we reconnect to the same gateway
	if (connected_gw == address)
	  return true;

	// remove previous bypass route
	remove_cmds_bypass_gw->execute(os);
	remove_cmds_bypass_gw->clear();

	ActionList::Ptr add_cmds = new ActionList();
	TUNMETHODS::add_bypass_route(tun_iface_name, address, ipv6, nullptr, *add_cmds, *remove_cmds_bypass_gw);

	// add gateway bypass route
	add_cmds->execute(os);
	return true;
      }

      int establish(const TunBuilderCapture& pull, // defined by TunBuilderSetup::Base
		    TunBuilderSetup::Config* config,
		    Stop* stop,
		    std::ostream& os) override
      {
	// get configuration
	Config *conf = dynamic_cast<Config *>(config);
	if (!conf)
	  throw tun_linux_error("missing config");

	int fd = -1;
	if (!conf->dco)
	  {
	    fd = open_tun(conf);
	  }
	else
	  {
	    // in DCO case device is already opened
	    tun_iface_name = conf->iface_name;
	  }

	ActionList::Ptr add_cmds = new ActionList();
	ActionList::Ptr remove_cmds_new = new ActionListReversed();

	// configure tun properties
	TUNMETHODS::tun_config(tun_iface_name,
			       pull,
			       nullptr,
			       *add_cmds,
			       *remove_cmds_new,
			       (conf->add_bypass_routes_on_establish ? TunConfigFlags::ADD_BYPASS_ROUTES : 0));

	// execute commands to bring up interface
	add_cmds->execute(os);

	// tear down old routes
	remove_cmds->execute(os);
	std::swap(remove_cmds, remove_cmds_new);

	connected_gw = pull.remote_address.to_string();

	return fd;
      }

    private:
      int open_tun(Config* conf)
      {
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
	tun_iface_name = ifr.ifr_name;

	return fd.release();
      }

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

      ActionList::Ptr remove_cmds_bypass_gw = new ActionList();
      ActionListReversed::Ptr remove_cmds = new ActionListReversed();

      std::string connected_gw;

      std::string tun_iface_name; // used to skip tun-based default gw when add bypass route
    };
  }
} // namespace openvpn

#endif // OPENVPN_TUN_LINUX_CLIENT_TUNCLI_H
