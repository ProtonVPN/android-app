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

// OpenVPN 3 kovpn-based tun interface

#include <cstring>
#include <openvpn/common/exception.hpp>
#include <openvpn/kovpn/kovpn.hpp>


#pragma once
namespace openvpn
{
  namespace KoTun
  {
    OPENVPN_EXCEPTION(kotun_error);

    struct DevConf
    {
	DevConf()
	    : dc{}
	{
	  std::memset(&dc, 0, sizeof(dc));
	}

	void set_dev_name(const std::string& name)
	{
	  if (name.length() < IFNAMSIZ)
	    ::strcpy(dc.dev_name, name.c_str());
	  else
	    OPENVPN_THROW(kotun_error, "ovpn dev name too long");
	}

	struct ovpn_dev_init dc;
    };
  }
}