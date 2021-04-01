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

// Tun interface utilities for Mac OS X.

#ifndef OPENVPN_TUN_MAC_TUNUTIL_H
#define OPENVPN_TUN_MAC_TUNUTIL_H

#include <fcntl.h>
#include <errno.h>

#include <string>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/asio/asioerr.hpp>
#include <openvpn/common/to_string.hpp>
#include <openvpn/common/scoped_fd.hpp>
#include <openvpn/tun/layer.hpp>

namespace openvpn {
  namespace TunMac {
    namespace Util {
      OPENVPN_EXCEPTION(tun_mac_util);

      inline int tuntap_open(const Layer& layer, std::string& name)
      {
 	for (int i = 0; i < 256; ++i)
	  {
	    const char *tuntap;
	    if (layer() == Layer::OSI_LAYER_3)
	      tuntap = "tun";
	    else if (layer() == Layer::OSI_LAYER_2)
	      tuntap = "tap";
	    else
	      throw tun_mac_util("unknown OSI layer");
	    const std::string node_str = tuntap + to_string(i);
	    const std::string node_fn = "/dev/" + node_str;

	    ScopedFD fd(open(node_fn.c_str(), O_RDWR));
	    if (fd.defined())
	      {
		// got it
		if (fcntl(fd(), F_SETFL, O_NONBLOCK) < 0)
		  throw tun_mac_util("fcntl error on " + node_fn + " : " + errinfo(errno));

		name = node_str;
		return fd.release();
	      }
	  }
	throw tun_mac_util(std::string("error opening Mac ") + layer.dev_type() + " device");
      }

    }
  }
}

#endif
