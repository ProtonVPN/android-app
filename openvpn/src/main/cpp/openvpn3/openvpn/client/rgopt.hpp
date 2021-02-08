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

// This class handles parsing and representation of redirect-gateway
// and redirect-private directives.

#ifndef OPENVPN_CLIENT_RGOPT_H
#define OPENVPN_CLIENT_RGOPT_H

#include <openvpn/common/options.hpp>

namespace openvpn {
  class RedirectGatewayFlags {
  public:
    enum Flags {
      RG_ENABLE      = (1<<0),
      RG_REROUTE_GW  = (1<<1),
      RG_LOCAL       = (1<<2),
      RG_AUTO_LOCAL  = (1<<3),
      RG_DEF1        = (1<<4),
      RG_BYPASS_DHCP = (1<<5),
      RG_BYPASS_DNS  = (1<<6),
      RG_BLOCK_LOCAL = (1<<7),
      RG_IPv4        = (1<<8),
      RG_IPv6        = (1<<9),

      RG_DEFAULT     = (RG_IPv4),
    };

    RedirectGatewayFlags() : flags_(RG_DEFAULT) {}

    RedirectGatewayFlags(unsigned int flags) : flags_(flags) {}

    explicit RedirectGatewayFlags(const OptionList& opt)
    {
      init(opt);
    }

    void init(const OptionList& opt)
    {
      flags_ = RG_DEFAULT;
      doinit(opt, "redirect-gateway", true);  // DIRECTIVE
      doinit(opt, "redirect-private", false); // DIRECTIVE
    }

    unsigned int operator()() const { return flags_; }

    bool redirect_gateway_ipv4_enabled() const
    {
      return rg_enabled() && (flags_ & RG_IPv4);
    }

    bool redirect_gateway_ipv6_enabled() const
    {
      return rg_enabled() && (flags_ & RG_IPv6);
    }

    bool redirect_gateway_local() const
    {
      return flags_ & RG_LOCAL;
    }

    std::string to_string() const
    {
      std::string ret;
      ret += "[ ";
      if (flags_ & RG_ENABLE)
	ret += "ENABLE ";
      if (flags_ & RG_REROUTE_GW)
	ret += "REROUTE_GW ";
      if (flags_ & RG_LOCAL)
	ret += "LOCAL ";
      if (flags_ & RG_AUTO_LOCAL)
	ret += "AUTO_LOCAL ";
      if (flags_ & RG_DEF1)
	ret += "DEF1 ";
      if (flags_ & RG_BYPASS_DHCP)
	ret += "BYPASS_DHCP ";
      if (flags_ & RG_BYPASS_DNS)
	ret += "BYPASS_DNS ";
      if (flags_ & RG_BLOCK_LOCAL)
	ret += "BLOCK_LOCAL ";
      if (flags_ & RG_IPv4)
	ret += "IPv4 ";
      if (flags_ & RG_IPv6)
	ret += "IPv6 ";
      ret += "]";
      return ret;
    }

  private:
    bool rg_enabled() const
    {
      return (flags_ & (RG_ENABLE|RG_REROUTE_GW)) == (RG_ENABLE|RG_REROUTE_GW);
    }

    void doinit(const OptionList& opt, const std::string& directive, const bool redirect_gateway)
    {
      OptionList::IndexMap::const_iterator rg = opt.map().find(directive);
      if (rg != opt.map().end())
	add_flags(opt, rg->second, redirect_gateway);
    }

    void add_flags(const OptionList& opt, const OptionList::IndexList& idx, const bool redirect_gateway)
    {
      flags_ |= RG_ENABLE;
      if (redirect_gateway)
	flags_ |= RG_REROUTE_GW;
      else
	flags_ &= ~RG_REROUTE_GW;
      for (OptionList::IndexList::const_iterator i = idx.begin(); i != idx.end(); ++i)
	{
	  const Option& o = opt[*i];
	  for (size_t j = 1; j < o.size(); ++j)
	    {
	      const std::string& f = o.get(j, 64);
	      if (f == "local")
		flags_ |= RG_LOCAL;
	      else if (f == "autolocal")
		flags_ |= RG_AUTO_LOCAL;
	      else if (f == "def1")
		flags_ |= RG_DEF1;
	      else if (f == "bypass-dhcp")
		flags_ |= RG_BYPASS_DHCP;
	      else if (f == "bypass-dns")
		flags_ |= RG_BYPASS_DNS;
	      else if (f == "block-local")
		flags_ |= RG_BLOCK_LOCAL;
	      else if (f == "ipv4")
		flags_ |= RG_IPv4;
	      else if (f == "!ipv4")
		flags_ &= ~RG_IPv4;
	      else if (f == "ipv6")
		flags_ |= RG_IPv6;
	      else if (f == "!ipv6")
		flags_ &= ~RG_IPv6;
	    }
	}
    }

    unsigned int flags_;
  };
}

#endif
