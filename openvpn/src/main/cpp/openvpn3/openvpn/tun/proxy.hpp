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

#pragma once

#include <openvpn/common/action.hpp>
#include <openvpn/tun/builder/capture.hpp>

namespace openvpn {
  class ProxySettings : public RC<thread_unsafe_refcount>
  {
  public:
    OPENVPN_EXCEPTION(proxy_error);

    typedef RCPtr<ProxySettings> Ptr;

    class ProxyAction : public Action
    {
    public:
      typedef RCPtr<ProxyAction> Ptr;

      ProxyAction(ProxySettings::Ptr parent_arg, bool del_arg)
	: parent(parent_arg), del(del_arg) { }

      virtual void execute(std::ostream& os) override
      {
	os << to_string() << std::endl;
	if (parent)
	  parent->set_proxy(del);
      }

      virtual std::string to_string() const override
      {
	std::ostringstream os;
	if (parent && parent->config.defined())
	  os << "ProxyAction: auto config: " << parent->config.to_string();
	return os.str();
      }

    private:
      const ProxySettings::Ptr parent;
      bool del;
    };

    ProxySettings(const TunBuilderCapture::ProxyAutoConfigURL& config_arg)
      : config(config_arg) { }

    virtual void set_proxy(bool del) = 0;

    template<class T>
    static void add_actions(const TunBuilderCapture& settings,
                            ActionList& create,
                            ActionList& destroy)
    {
      ProxySettings::Ptr proxy(new T(settings.proxy_auto_config_url));
      ProxyAction::Ptr create_action(new ProxyAction(proxy, false));
      ProxyAction::Ptr destroy_action(new ProxyAction(proxy, true));
      create.add(create_action);
      destroy.add(destroy_action);
    }

    const std::string sname = "OpenVPNConnect";

    TunBuilderCapture::ProxyAutoConfigURL config;
  };
}
