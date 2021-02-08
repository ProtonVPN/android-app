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

#ifndef OPENVPN_CLIENT_OPTFILT_H
#define OPENVPN_CLIENT_OPTFILT_H

#include <openvpn/common/options.hpp>

// Options filters

namespace openvpn {

  class PushedOptionsFilter : public OptionList::FilterBase
  {
  public:
    PushedOptionsFilter(const bool route_nopull)
      : route_nopull_(route_nopull) {}

    virtual bool filter(const Option& opt)
    {
      const bool ret = filt(opt);
      if (!ret)
	OPENVPN_LOG("Ignored due to route-nopull: " << opt.render(Option::RENDER_TRUNC_64|Option::RENDER_BRACKET));
      return ret;
    }

  private:
    // return false if pushed option should be ignored due to route-nopull directive.
    bool filt(const Option& opt)
    {
      if (route_nopull_)
	{
	  if (opt.size() >= 1)
	    {
	      const std::string& directive = opt.ref(0);
	      if (directive.length() >= 1)
		{
		  switch (directive[0])
		    {
		    case 'b':
		      if (directive == "block-ipv6")
			return false;
		      break;
		    case 'c':
		      if (directive == "client-nat")
			return false;
		      break;
		    case 'd':
		      if (directive == "dhcp-option" ||
			  directive == "dhcp-renew" ||
			  directive == "dhcp-pre-release" ||
			  directive == "dhcp-release")
			return false;
		      break;
		    case 'i':
		      if (directive == "ip-win32")
			return false;
		      break;
		    case 'r':
		      if (directive == "route" ||
			  directive == "route-ipv6" ||
			  directive == "route-metric" ||
			  directive == "redirect-gateway" ||
			  directive == "redirect-private" ||
			  directive == "register-dns" ||
			  directive == "route-delay" ||
			  directive == "route-method")
			return false;
		      break;
		    case 't':
		      if (directive == "tap-sleep")
			return false;
		      break;
		    }
		}
	    }
	}
      return true;
    }

    bool route_nopull_;
  };

}

#endif
