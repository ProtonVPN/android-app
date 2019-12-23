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

#ifndef OPENVPN_TUN_MAC_MACGW_H
#define OPENVPN_TUN_MAC_MACGW_H

#include <string>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/addr/ip.hpp>
#include <openvpn/apple/scdynstore.hpp>
#include <openvpn/apple/cf/cfhelper.hpp>

namespace openvpn {
  struct MacGWInfo
  {
    struct Variant
    {
      friend struct MacGWInfo;
    public:
      bool defined() const {
	return !iface.empty() && router.defined();
      }

      std::string to_string() const
      {
	return iface + '/' + router.to_string();
      }

      std::string iface;
      IP::Addr router;

    private:
      Variant() {}

      Variant(const IP::Addr::Version v, const CF::DynamicStore& dstore)
      {
	const std::string key = std::string("State:/Network/Global/IP") + IP::Addr::version_string_static(v);
	const CF::Dict d(CF::DynamicStoreCopyDict(dstore, key));
	iface = CF::dict_get_str(d, "PrimaryInterface");
	const std::string addr = CF::dict_get_str(d, "Router");
	if (!addr.empty())
	  router = IP::Addr::from_string(addr, "MacGWInfo::Variant", v);
	else
	  router.reset();
      }
    };

    MacGWInfo()
    {
      const CF::DynamicStore ds(SCDynamicStoreCreate(kCFAllocatorDefault,
						     CFSTR("MacGWInfo"),
						     nullptr,
						     nullptr));
      v4 = Variant(IP::Addr::V4, ds);
      v6 = Variant(IP::Addr::V6, ds);
    }

    std::string to_string() const
    {
      return "IPv4=" + v4.to_string() + " IPv6=" + v6.to_string();
    }

    Variant v4;
    Variant v6;
  };
}

#endif
