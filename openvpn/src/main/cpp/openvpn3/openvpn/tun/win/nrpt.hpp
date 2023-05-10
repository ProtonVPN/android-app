//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2022 OpenVPN Inc.
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
//

// Name Resolution Policy Table (NRPT) utilities for Windows

#ifndef OPENVPN_TUN_WIN_NRPT_H
#define OPENVPN_TUN_WIN_NRPT_H

#include <string>
#include <sstream>
#include <vector>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/string.hpp>
#include <openvpn/common/wstring.hpp>
#include <openvpn/common/action.hpp>
#include <openvpn/win/reg.hpp>
#include <openvpn/win/winerr.hpp>

namespace openvpn {
namespace TunWin {

// NRPT rules described here: https://msdn.microsoft.com/en-us/library/ff957356.aspx
class NRPT
{
  public:
    OPENVPN_EXCEPTION(nrpt_error);

    static void create_rule(const std::vector<std::string> names, const std::vector<std::string> dns_servers)
    {
        Win::RegKey key;

        for (size_t i = 0; i < names.size(); ++i)
        {
            // open/create the key
            {
                std::ostringstream ss;
                ss << dnsPolicyConfig() << "\\" << policyPrefix() << "-" << i;
                auto key_name = ss.str();

                const LONG status = ::RegCreateKeyA(HKEY_LOCAL_MACHINE, key_name.c_str(), key.ref());
                check_reg_error<nrpt_error>(status, key_name);
            }

            // Name
            {
                std::wstring name(wstring::from_utf8(names[i]));
                name += L'\0';
                const LONG status = ::RegSetValueExW(key(),
                                                     L"Name",
                                                     0,
                                                     REG_MULTI_SZ,
                                                     (const BYTE *)name.c_str(),
                                                     (name.length() + 1) * 2);
                check_reg_error<nrpt_error>(status, "Name");
            }

            // GenericDNSServers
            {
                const std::wstring dns_servers_joined = wstring::from_utf8(string::join(dns_servers, ";"));
                const LONG status = ::RegSetValueExW(key(),
                                                     L"GenericDNSServers",
                                                     0,
                                                     REG_SZ,
                                                     (const BYTE *)dns_servers_joined.c_str(),
                                                     (dns_servers_joined.length() + 1) * 2);
                check_reg_error<nrpt_error>(status, "GenericDNSServers");
            }

            // ConfigOptions
            {
                const DWORD value = 0x8; // Only the Generic DNS server option (that is, the option defined in section 2.2.2.13) is specified.
                const LONG status = ::RegSetValueExW(key(),
                                                     L"ConfigOptions",
                                                     0,
                                                     REG_DWORD,
                                                     (const BYTE *)&value,
                                                     sizeof(value));
                check_reg_error<nrpt_error>(status, "ConfigOptions");
            }

            // Version
            {
                const DWORD value = 0x2;
                const LONG status = ::RegSetValueExW(key(),
                                                     L"Version",
                                                     0,
                                                     REG_DWORD,
                                                     (const BYTE *)&value,
                                                     sizeof(value));
                check_reg_error<nrpt_error>(status, "Version");
            }
        }
    }

    static bool delete_rule()
    {
        Win::RegKeyEnumerator keys(HKEY_LOCAL_MACHINE, dnsPolicyConfig());

        for (const auto &key : keys)
        {
            // remove only own policies
            if (key.find(policyPrefix()) == std::string::npos)
                continue;

            std::ostringstream ss;
            ss << dnsPolicyConfig() << "\\" << key;
            auto path = ss.str();
            ::RegDeleteTreeA(HKEY_LOCAL_MACHINE, path.c_str());
        }

        return true;
    }

  private:
    static const char *dnsPolicyConfig()
    {
        static const char subkey[] = "SYSTEM\\CurrentControlSet\\Services\\Dnscache\\Parameters\\DnsPolicyConfig";
        return subkey;
    }

    static const char *policyPrefix()
    {
        static const char prefix[] = "OpenVPNDNSRouting";
        return prefix;
    }

  public:
    class ActionCreate : public Action
    {
      public:
        ActionCreate(const std::vector<std::string> &names_arg,
                     const std::vector<std::string> &dns_servers_arg)
            : names(names_arg),
              dns_servers(dns_servers_arg)
        {
        }

        virtual void execute(std::ostream &log) override
        {
            log << to_string() << std::endl;
            create_rule(names, dns_servers);
        }

        virtual std::string to_string() const override
        {
            std::ostringstream os;
            os << "NRPT::ActionCreate"
               << " names=[" << string::join(names, ",") << "]"
               << " dns_servers=[" << string::join(dns_servers, ",") << "]";
            return os.str();
        }

      private:
        const std::vector<std::string> names;
        const std::vector<std::string> dns_servers;
    };

    class ActionDelete : public Action
    {
      public:
        virtual void execute(std::ostream &log) override
        {
            log << to_string() << std::endl;
            delete_rule();
        }

        virtual std::string to_string() const override
        {
            return "NRPT::ActionDelete";
        }
    };
};

} // namespace TunWin
} // namespace openvpn

#endif
