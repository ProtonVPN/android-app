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

// Parse the argument of a "tun" or "tap" directive.  Also parse an optional
// "/v4" or "/v6" after the tun name to denote IPv4 or IPv6 usage.

#ifndef OPENVPN_TUN_TUNSPEC_H
#define OPENVPN_TUN_TUNSPEC_H

#include <string>

#include <openvpn/common/split.hpp>

namespace openvpn {

  struct ParseTunSpec
  {
    OPENVPN_EXCEPTION(bad_tun_spec);

    ParseTunSpec(const std::string& tun_spec)
      : ipv6(false)
    {
      std::vector<std::string> s = Split::by_char<std::vector<std::string>, NullLex, Split::NullLimit>(tun_spec, '/');
      if (s.size() == 1)
	{
	  tun_name = s[0];
	}
      else if (s.size() == 2)
	{
	  tun_name = s[0];
	  if (s[1] == "v4")
	    ipv6 = false;
	  else if (s[1] == "v6")
	    ipv6 = true;
	  else
	    throw bad_tun_spec();
	}
      else
	throw bad_tun_spec();
    }
    bool ipv6;
    std::string tun_name;
  };

} // namespace openvpn

#endif // OPENVPN_TUN_TUNSPEC_H
