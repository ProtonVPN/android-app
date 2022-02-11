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

// Get the local MAC addr of the interface that owns the default route

#ifndef OPENVPN_NETCONF_HWADDR_H
#define OPENVPN_NETCONF_HWADDR_H

#include <string>

#include <openvpn/common/platform.hpp>
#include <openvpn/addr/macaddr.hpp>

#if defined(OPENVPN_PLATFORM_WIN) && !defined(OPENVPN_PLATFORM_UWP)
#include <openvpn/tun/win/tunutil.hpp>
#elif defined(OPENVPN_PLATFORM_MAC)
#include <openvpn/tun/mac/gwv4.hpp>
#elif defined(TARGET_OS_IPHONE)
#include <UIKit/UIKit.h>
#endif

namespace openvpn {
  inline std::string get_hwaddr()
  {
#if defined(OPENVPN_PLATFORM_WIN) && !defined(OPENVPN_PLATFORM_UWP)
    const TunWin::Util::BestGateway dg;
    if (dg.defined())
      {
	const TunWin::Util::IPAdaptersInfo ai_list;
	const IP_ADAPTER_INFO* ai = ai_list.adapter(dg.interface_index());
	if (ai)
	  {
	    const MACAddr mac(ai->Address);
	    return mac.to_string();
	  }
      }
#elif defined(OPENVPN_PLATFORM_MAC)
    const MacGatewayInfoV4 gw;
    if (gw.hwaddr_defined())
      {
	const MACAddr& mac = gw.hwaddr();
	return mac.to_string();
      }
#elif defined(TARGET_OS_IPHONE)
    // as reported at https://developer.apple.com/library/content/releasenotes/General/WhatsNewIniOS/Articles/iOS7.html#//apple_ref/doc/uid/TP40013162-SW34
    // we can't get the MAC address from iOS for privacy reasons, but we can
    // use the UUID as unique identifier. It is unique among the App domain,
    // meaning that a different app will get a different UUID from this call
    const NSString *uuid = [[[UIDevice currentDevice] identifierForVendor] UUIDString];
    return std::string([uuid UTF8String]);
#endif
    return std::string();
  }
}

#endif
