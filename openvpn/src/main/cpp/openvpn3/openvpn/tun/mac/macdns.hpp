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

// DNS utilities for Mac OS X.

#ifndef OPENVPN_TUN_MAC_MACDNS_H
#define OPENVPN_TUN_MAC_MACDNS_H

#include <string>
#include <sstream>
#include <memory>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/string.hpp>
#include <openvpn/common/process.hpp>
#include <openvpn/apple/macver.hpp>
#include <openvpn/apple/scdynstore.hpp>
#include <openvpn/apple/cf/cfhelper.hpp>
#include <openvpn/tun/builder/capture.hpp>
#include <openvpn/tun/mac/dsdict.hpp>

namespace openvpn {
  class MacDNS : public RC<thread_unsafe_refcount>
  {
    class Info;

  public:
    typedef RCPtr<MacDNS> Ptr;

    OPENVPN_EXCEPTION(macdns_error);

    class Config : public RC<thread_safe_refcount>
    {
    public:
      typedef RCPtr<Config> Ptr;

      Config()
      {
      }

      Config(const TunBuilderCapture& settings)
	: dns_servers(get_dns_servers(settings)),
	  search_domains(get_search_domains(settings)),
	  adapter_domain_suffix(settings.adapter_domain_suffix)
      {
	// We redirect DNS if either of the following is true:
	// 1. redirect-gateway (IPv4) is pushed, or
	// 2. DNS servers are pushed but no search domains are pushed
	redirect_dns = settings.reroute_gw.ipv4 || (CF::array_len(dns_servers) && !CF::array_len(search_domains));
      }

      std::string to_string() const
      {
	std::ostringstream os;
	os << "RD=" << redirect_dns;
	os << " SO=" << search_order;
	os << " DNS=" << CF::array_to_string(dns_servers);
	os << " DOM=" << CF::array_to_string(search_domains);
	os << " ADS=" << adapter_domain_suffix;
	return os.str();
      }

      bool redirect_dns = false;
      int search_order = 5000;
      CF::Array dns_servers;
      CF::Array search_domains;
      std::string adapter_domain_suffix;

    private:
      static CF::Array get_dns_servers(const TunBuilderCapture& settings)
      {
	CF::MutableArray ret(CF::mutable_array());
	for (std::vector<TunBuilderCapture::DNSServer>::const_iterator i = settings.dns_servers.begin();
	     i != settings.dns_servers.end(); ++i)
	  {
	    const TunBuilderCapture::DNSServer& ds = *i;
	    CF::array_append_str(ret, ds.address);
	  }
	return CF::const_array(ret);
      }

      static CF::Array get_search_domains(const TunBuilderCapture& settings)
      {
	CF::MutableArray ret(CF::mutable_array());
	for (std::vector<TunBuilderCapture::SearchDomain>::const_iterator i = settings.search_domains.begin();
	     i != settings.search_domains.end(); ++i)
	  {
	    const TunBuilderCapture::SearchDomain& sd = *i;
	    CF::array_append_str(ret, sd.domain);
	  }
	return CF::const_array(ret);
      }
    };

    MacDNS(const std::string& sname_arg)
      : sname(sname_arg)
    {
    }

    void flush_cache()
    {
      const int v = ver.major();
      if (v < Mac::Version::OSX_10_6)
	OPENVPN_LOG("MacDNS: Error: No support for Mac OS X versions earlier than 10.6");
      if (v == Mac::Version::OSX_10_6 || v >= Mac::Version::OSX_10_9)
	{
	  Argv args;
	  args.push_back("/usr/bin/dscacheutil");
	  args.push_back("-flushcache");
	  OPENVPN_LOG(args.to_string());
	  system_cmd(args);
	}
      if (v >= Mac::Version::OSX_10_7)
	{
	  Argv args;
	  args.push_back("/usr/bin/killall");
	  args.push_back("-HUP");
	  args.push_back("mDNSResponder");
	  OPENVPN_LOG(args.to_string());
	  system_cmd(args);
	}
    }

    bool signal_network_reconfiguration()
    {
      return DSDict::signal_network_reconfiguration(sname);
    }

    bool setdns(const Config& config)
    {
      bool mod = false;

      try {
	CF::DynamicStore sc = ds_create();
	Info::Ptr info(new Info(sc, sname));

	// cleanup settings applied to previous interface
	interface_change_cleanup(info.get());

	if (config.redirect_dns)
	  {
	    // redirect all DNS
	    info->dns.will_modify();

	    // set DNS servers
	    if (CF::array_len(config.dns_servers))
	      {
		info->dns.backup_orig("ServerAddresses");
		CF::dict_set_obj(info->dns.mod, "ServerAddresses", config.dns_servers());
	      }

	    // set search domains
	    info->dns.backup_orig("SearchDomains");
	    CF::MutableArray search_domains(CF::mutable_array());

	    // add adapter_domain_suffix to SearchDomains for domain autocompletion
	    if (config.adapter_domain_suffix.length() > 0)
	      CF::array_append_str(search_domains, config.adapter_domain_suffix);

	    if (CF::array_len(search_domains))
	      CF::dict_set_obj(info->dns.mod, "SearchDomains", search_domains());

	    // set search order
	    info->dns.backup_orig("SearchOrder");
	    CF::dict_set_int(info->dns.mod, "SearchOrder", config.search_order);

	    // push it
	    mod |= info->dns.push_to_store();
	  }
	else
	  {
	    // split-DNS - resolve only specific domains
	    info->ovpn.mod_reset();
	    if (CF::array_len(config.dns_servers) && CF::array_len(config.search_domains))
	      {
		// set DNS servers
		CF::dict_set_obj(info->ovpn.mod, "ServerAddresses", config.dns_servers());

		// DNS will be used only for those domains
		CF::dict_set_obj(info->ovpn.mod, "SupplementalMatchDomains", config.search_domains());
  
		// do not use those domains in autocompletion
		CF::dict_set_int(info->ovpn.mod, "SupplementalMatchDomainsNoSearch", 1);
	      }
       
	    // in case of split-DNS macOS uses domain suffix of network adapter,
	    // not the one provided by VPN (which we put to SearchDomains)

	    // push it
	    mod |= info->ovpn.push_to_store();
	  }

	if (mod)
	  {
            // As a backup, save PrimaryService in private dict (if network goes down while
            // we are set, we can lose info about PrimaryService in State:/Network/Global/IPv4
            // and be unable to reset ourselves).
	    const CFTypeRef ps = CF::dict_get_obj(info->ipv4.dict, "PrimaryService");
	    if (ps)
	      {
		info->info.mod_reset();
		CF::dict_set_obj(info->info.mod, "PrimaryService", ps);
		info->info.push_to_store();
	      }
	  }

	prev = info;
	if (mod)
	  OPENVPN_LOG("MacDNS: SETDNS " << ver.to_string() << std::endl << info->to_string());
      }
      catch (const std::exception& e)
	{
	  OPENVPN_LOG("MacDNS: setdns exception: " << e.what());
	}
      return mod;
    }

    bool resetdns()
    {
      bool mod = false;
      try {
	CF::DynamicStore sc = ds_create();
	Info::Ptr info(new Info(sc, sname));

	// cleanup settings applied to previous interface
	interface_change_cleanup(info.get());

	// undo primary dns changes
	mod |= reset_primary_dns(info.get());

	// undo non-redirect-gateway changes
	if (CF::dict_len(info->ovpn.dict))
	  mod |= info->ovpn.remove_from_store();

	// remove private info dict
	if (CF::dict_len(info->info.dict))
	  mod |= info->info.remove_from_store();

	if (mod)
	  OPENVPN_LOG("MacDNS: RESETDNS " << ver.to_string() << std::endl << info->to_string());
      }
      catch (const std::exception& e)
	{
	  OPENVPN_LOG("MacDNS: resetdns exception: " << e.what());
	}
      return mod;
    }

    std::string to_string() const
    {
      CF::DynamicStore sc = ds_create();
      Info::Ptr info(new Info(sc, sname));
      return info->to_string();
    }

    CF::Array dskey_array() const
    {
      CF::DynamicStore sc = ds_create();
      Info::Ptr info(new Info(sc, sname));
      CF::MutableArray ret(CF::mutable_array());
      CF::array_append_str(ret, info->ipv4.dskey);
      CF::array_append_str(ret, info->info.dskey);
      CF::array_append_str(ret, info->ovpn.dskey);
      CF::array_append_str(ret, info->dns.dskey);
      return CF::const_array(ret);
    }

  private:
    void interface_change_cleanup(Info* info)
    {
      if (info->interface_change(prev.get()))
	{
	  reset_primary_dns(prev.get());
	  prev.reset();
	}
    }

    bool reset_primary_dns(Info* info)
    {
      bool mod = false;
      if (info)
	{
#if 1
	  // Restore previous DNS settings.
	  // Recommended for production.
	  info->dns.will_modify();
	  info->dns.restore_orig();
	  mod |= info->dns.push_to_store();
#else
	  // Wipe DNS settings without restore.
	  // This can potentially wipe static IP/DNS settings.
	  info->dns.mod_reset();
	  mod |= info->dns.push_to_store();
#endif
	}
      return mod;
    }
    
    class Info : public RC<thread_unsafe_refcount>
    {
    public:
      typedef RCPtr<Info> Ptr;

      Info(CF::DynamicStore& sc, const std::string& sname)
	: ipv4(sc, sname, "State:/Network/Global/IPv4"),
	  info(sc, sname, "State:/Network/Service/" + sname + "/Info"),
	  ovpn(sc, sname, "State:/Network/Service/" + sname + "/DNS"),
	  dns(sc, sname, primary_dns(ipv4.dict, info.dict))
      {
      }

      std::string to_string() const
      {
	std::ostringstream os;
	os << ipv4.to_string();
	os << info.to_string();
	os << ovpn.to_string();
	os << dns.to_string();
	return os.str();
      }

      bool interface_change(Info* other) const
      {
	return other && dns.dskey != other->dns.dskey;
      }

      DSDict ipv4;
      DSDict info; // we may modify
      DSDict ovpn; // we may modify
      DSDict dns;  // we may modify

    private:
      static std::string primary_dns(const CF::Dict& ipv4, const CF::Dict& info)
      {
	std::string serv = CF::dict_get_str(ipv4, "PrimaryService");
	if (serv.empty())
	  serv = CF::dict_get_str(info, "PrimaryService");
	if (serv.empty())
	  throw macdns_error("no primary service");
	return "Setup:/Network/Service/" + serv + "/DNS";
      }
    };

    CF::DynamicStore ds_create() const
    {
      return DSDict::ds_create(sname);
    }

    const std::string sname;
    Mac::Version ver;
    Info::Ptr prev;
  };
}

#endif
