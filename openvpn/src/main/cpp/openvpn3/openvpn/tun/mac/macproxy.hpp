//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2018 OpenVPN Inc.
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

#pragma once

#include <openvpn/tun/proxy.hpp>
#include <openvpn/tun/mac/dsdict.hpp>

namespace openvpn {
  class MacProxySettings : public ProxySettings
  {
  public:
    OPENVPN_EXCEPTION(macproxy_error);
  
    typedef RCPtr<MacProxySettings> Ptr;
    
    class Info : public RC<thread_unsafe_refcount>
    {
    public:
      typedef RCPtr<Info> Ptr;

      Info(CF::DynamicStore& sc, const std::string& sname)
        : ipv4(sc, sname, "State:/Network/Global/IPv4"),
          info(sc, sname, "State:/Network/Service/" + sname + "/Info"),
          proxy(sc, sname, proxies(ipv4.dict, info.dict)) { }

      std::string to_string() const
      {
        std::ostringstream os;
        os << ipv4.to_string();
        os << info.to_string();
        os << proxy.to_string();
        return os.str();
      }

      DSDict ipv4;
      DSDict info;
      DSDict proxy;

    private:
      static std::string proxies(const CF::Dict& ipv4, const CF::Dict& info)
      {
	std::string serv = CF::dict_get_str(ipv4, "PrimaryService");
	if (serv.empty())
	  serv = CF::dict_get_str(info, "PrimaryService");
	if (serv.empty())
	  throw macproxy_error("no primary service");
	return "Setup:/Network/Service/" + serv + "/Proxies";
      }
    };
    
    MacProxySettings(const TunBuilderCapture::ProxyAutoConfigURL& config_arg)
      : ProxySettings(config_arg) { }
    
    void set_proxy(bool del) override
    {
      if (!config.defined())
	return;
	
      CF::DynamicStore sc = DSDict::ds_create(sname);
      Info::Ptr info(new Info(sc, sname));
	
      info->proxy.will_modify();
	
      if (!del)
	{
	  info->proxy.backup_orig("ProxyAutoConfigEnable");
	  CF::dict_set_int(info->proxy.mod, "ProxyAutoConfigEnable", 1);

	  info->proxy.backup_orig("ProxyAutoConfigURLString");
	  CF::dict_set_str(info->proxy.mod, "ProxyAutoConfigURLString", config.to_string());
	}
      else
	info->proxy.restore_orig();

      info->proxy.push_to_store();

      OPENVPN_LOG("MacProxy: set_proxy " << info->to_string());
    }
  };
}
