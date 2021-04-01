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

#include <string>

#include <openvpn/apple/reach.hpp>
#include <openvpn/netconf/enumiface.hpp>

#ifndef OPENVPN_APPLECRYPTO_UTIL_IOSACTIVEIFACE_H
#define OPENVPN_APPLECRYPTO_UTIL_IOSACTIVEIFACE_H

namespace openvpn {

  class iOSActiveInterface : public ReachabilityInterface
  {
  public:
    virtual Status reachable() const
    {
      if (ei.iface_up("en0"))
	return ReachableViaWiFi;
      else if (ei.iface_up("pdp_ip0"))
	return ReachableViaWWAN;
      else
	return NotReachable;
    }

    virtual bool reachableVia(const std::string& net_type) const
    {
      const Status r = reachable();
      if (net_type == "cellular")
	return r == ReachableViaWWAN;
      else if (net_type == "wifi")
	return r == ReachableViaWiFi;
      else
	return r != NotReachable;
    }

    virtual std::string to_string() const
    {
      switch (reachable())
	{
	case ReachableViaWiFi:
	  return "ReachableViaWiFi";
	case ReachableViaWWAN:
	  return "ReachableViaWWAN";
	case NotReachable:
	  return "NotReachable";
	}
    }

  private:
    EnumIface ei;
  };

}
#endif
