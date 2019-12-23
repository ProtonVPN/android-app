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

// Regular expressions for IPv4/v6
// Source: http://stackoverflow.com/questions/53497/regular-expression-that-matches-valid-ipv6-addresses

#ifndef OPENVPN_ADDR_REGEX_H
#define OPENVPN_ADDR_REGEX_H

#include <string>

namespace openvpn {
  namespace IP {
    inline std::string v4_regex()
    {
      const std::string ipv4seg  = "(?:25[0-5]|(?:2[0-4]|1{0,1}[0-9]){0,1}[0-9])";
      return "(?:" + ipv4seg + "\\.){3,3}" + ipv4seg;
    }

    inline std::string v6_regex()
    {
      const std::string ipv6seg = "[0-9a-fA-F]{1,4}";
      return "(?:"
	"(?:" + ipv6seg + ":){7,7}" + ipv6seg + "|"               // 1:2:3:4:5:6:7:8
	"(?:" + ipv6seg + ":){1,7}:|"                             // 1::                                 1:2:3:4:5:6:7::
	"(?:" + ipv6seg + ":){1,6}:" + ipv6seg + "|"              // 1::8               1:2:3:4:5:6::8   1:2:3:4:5:6::8
	"(?:" + ipv6seg + ":){1,5}(?::" + ipv6seg + "){1,2}|"     // 1::7:8             1:2:3:4:5::7:8   1:2:3:4:5::8
	"(?:" + ipv6seg + ":){1,4}(?::" + ipv6seg + "){1,3}|"     // 1::6:7:8           1:2:3:4::6:7:8   1:2:3:4::8
	"(?:" + ipv6seg + ":){1,3}(?::" + ipv6seg + "){1,4}|"     // 1::5:6:7:8         1:2:3::5:6:7:8   1:2:3::8
	"(?:" + ipv6seg + ":){1,2}(?::" + ipv6seg + "){1,5}|" +   // 1::4:5:6:7:8       1:2::4:5:6:7:8   1:2::8
	ipv6seg + ":(?:(?::" + ipv6seg + "){1,6})|"               // 1::3:4:5:6:7:8     1::3:4:5:6:7:8   1::8
	":(?:(?::" + ipv6seg + "){1,7}|:)|"                       // ::2:3:4:5:6:7:8    ::2:3:4:5:6:7:8  ::8       ::
	"fe80:(?::" + ipv6seg + "){0,4}%[0-9a-zA-Z]{1,}|"         // fe80::7:8%eth0     fe80::7:8%1  (link-local IPv6 addresses with zone index)
	"::(?:ffff(?::0{1,4}){0,1}:){0,1}" + v4_regex() + "|"     // ::255.255.255.255  ::ffff:255.255.255.255  ::ffff:0:255.255.255.255 (IPv4-mapped IPv6 addresses and IPv4-translated addresses)
	"(?:" + ipv6seg + ":){1,4}:" + v4_regex() +               // 2001:db8:3:4::192.0.2.33  64:ff9b::192.0.2.33 (IPv4-Embedded IPv6 Address)
	")";
    }
  }
}

#endif
