//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012- OpenVPN Inc.
//
//    SPDX-License-Identifier: MPL-2.0 OR AGPL-3.0-only WITH openvpn3-openssl-exception
//
//

/**
 * @brief Name Resolution Policy Table (NRPT) utilities for Windows
 *
 * NRPT rules define how DNS loop-ups are done on Windows systems. They
 * override the traditional settings, that are done with the network adapters,
 * so having NRPT rules in place, only those will define how DNS works.
 *
 * There are two subkey in the Registry where NRPT rules can be defined. One
 * for rules coming in via group policies and the other for locally defined rules.
 * Group policy rules are preferred and if they exist, local rules will be ignored.
 *
 * OpenVPN will find the right subkey to add its rules to. In case there is no
 * split DNS rule defined it will also add so called bypass rules, which make sure
 * local name resolution will still work while the VPN is connected. This is done
 * by collecting the name server addresses from the adapter configurations and
 * adding them as NRPT rules for the adapter's domain suffix.
 *
 * NRPT rules described here: https://msdn.microsoft.com/en-us/library/ff957356.aspx
 */

#pragma once

#include <string>
#include <sstream>
#include <vector>
#include <array>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/string.hpp>
#include <openvpn/common/wstring.hpp>
#include <openvpn/common/action.hpp>
#include <openvpn/win/reg.hpp>
#include <openvpn/win/netutil.hpp>
#include <openvpn/win/winerr.hpp>

namespace openvpn::TunWin {

/**
 * @brief Manage NRPT rules for Windows
 *
 * @tparam REG      Registry abstraction class to use
 * @tparam NETAPI   Network related Win32 API class to use
 */
template <typename REG, typename NETAPI>
class Nrpt
{
  public:
    OPENVPN_EXCEPTION(nrpt_error);

    /**
     * @brief Create a NRPT rule in the registry
     *
     * The exact location of the rule depends on whether there are alredy rules
     * rules defined. If so the rule is stored with them, either in the place
     * where group policy based ones are, or the local one.
     *
     * @param rule_id       the unique rule id
     * @param domains       domains the rule applies to as wide MULTI_SZ
     * @param servers       list of name server addresses, separated by semicolon
     * @param dnssec        whether DNSSEC should be enabled for the rule
     */
    static void create_rule(const std::string &rule_id,
                            const std::wstring &domains,
                            const std::wstring &servers,
                            bool dnssec)
    {
        LSTATUS status;

        // Open / create the key
        typename REG::Key nrpt = open_nrpt_base_key();
        if (!nrpt.defined())
        {
            throw nrpt_error("cannot open NRPT base key");
        }
        typename REG::Key rule_key(nrpt(), wstring::from_utf8(rule_id), true);
        if (!rule_key.defined())
        {
            throw nrpt_error("cannot create NRPT rule subkey");
        }

        // Name
        status = REG::set_multi_string(rule_key, L"Name", domains);
        check_reg_error<nrpt_error>(status, "Name");

        // GenericDNSServers
        status = REG::set_string(rule_key, L"GenericDNSServers", servers);
        check_reg_error<nrpt_error>(status, "GenericDNSServers");

        // DNSSEC
        if (dnssec)
        {
            status = REG::set_dword(rule_key, L"DNSSECValidationRequired", 1);
            check_reg_error<nrpt_error>(status, "DNSSECValidationRequired");

            status = REG::set_dword(rule_key, L"DNSSECQueryIPSECRequired", 0);
            check_reg_error<nrpt_error>(status, "DNSSECQueryIPSECRequired");

            status = REG::set_dword(rule_key, L"DNSSECQueryIPSECEncryption", 0);
            check_reg_error<nrpt_error>(status, "DNSSECQueryIPSECEncryption");
        }

        // ConfigOptions
        // 0x8: Only the Generic DNS server option is specified.
        // 0xA: The Generic DNS server option and the DNSSEC options are specified
        status = REG::set_dword(rule_key, L"ConfigOptions", dnssec ? 0xA : 0x8);
        check_reg_error<nrpt_error>(status, "ConfigOptions");

        // Version
        status = REG::set_dword(rule_key, L"Version", 2);
        check_reg_error<nrpt_error>(status, "Version");
    }

    /**
     * Set NRPT exclude rules to accompany a catch all rule. This is done so that
     * local resolution of names is not interfered with in case the VPN resolves
     * all names. Exclude rules are only installed if the DNS settings came in via
     * --dns options, to keep backwards compatibility.
     *
     * @param process_id    the process id used for the rules
     */
    static void create_exclude_rules(DWORD process_id)
    {
        std::uint32_t n = 0;
        const auto data = collect_exclude_rule_data();
        for (const auto &exclude : data)
        {
            const auto id = exclude_rule_id(process_id, n++);
            create_rule(id, exclude.domains, string::join(exclude.addresses, L";"), false);
        }
    }

    /**
     * @brief Remove our NRPT rules from the registry
     *
     * Iterate over the rules in the two know subkeys where NRPT rules can be located
     * in the Windows registry and remove those rules, which we identify as ours. This
     * is done by comparing the process id we add to the end of each rule we add. If
     * the process id is zero all NRPT rules are deleted, regardless of the actual pid.
     *
     * @param process_id    the process id used for the rule deletion
     */
    static void delete_rules(DWORD process_id)
    {
        std::vector<std::wstring> del_subkeys;
        static constexpr std::array<PCWSTR, 2> nrpt_subkeys{
            REG::gpol_nrpt_subkey, REG::local_nrpt_subkey};
        // Only find rules to delete, so that the iterator stays valid
        for (const auto &nrpt_subkey : nrpt_subkeys)
        {
            const auto pid = L"-" + std::to_wstring(process_id);
            typename REG::Key nrpt_key(nrpt_subkey);
            typename REG::KeyEnumerator nrpt_rules(nrpt_key);

            for (const auto &nrpt_rule_id : nrpt_rules)
            {
                // remove only own policies
                if (nrpt_rule_id.find(wstring::from_utf8(id_prefix())) != 0)
                    continue;
                if (process_id && nrpt_rule_id.rfind(pid) != (nrpt_rule_id.size() - pid.size()))
                    continue;

                std::wostringstream rule_subkey;
                rule_subkey << nrpt_subkey << L"\\" << nrpt_rule_id;
                del_subkeys.push_back(rule_subkey.str());
            }
        }
        // Now delete the rules
        for (const auto &subkey : del_subkeys)
        {
            REG::delete_subkey(subkey);
        }
    }

  private:
    /**
     * Holds the information for one NRPT exclude rule, i.e. data from
     * local DNS configuration. Note that 'domains' is a MULTI_SZ string.
     */
    struct ExcludeRuleData
    {
        std::wstring domains;
        std::vector<std::wstring> addresses;
    };

    /**
     * @brief Get IPv4 DNS server addresses of an interface
     *
     * @param  itf_guid                     The interface GUID string
     * @return std::vector<std::wstring>    IPv4 server addresses found
     */
    static std::vector<std::wstring> interface_ipv4_dns_servers(const std::wstring &itf_guid)
    {
        typename REG::Key itf_key(std::wstring(REG::subkey_ipv4_itfs) + L"\\" + itf_guid);

        auto [servers, error] = REG::get_string(itf_key, L"NameServer");
        if (!error && !servers.empty())
        {
            return string::split(servers, ',');
        }

        if (dhcp_enabled_on_itf<REG>(itf_key))
        {
            auto [servers, error] = REG::get_string(itf_key, L"DhcpNameServer");
            if (!error && !servers.empty())
            {
                return string::split(servers, ' ');
            }
        }

        return {};
    }

    /**
     * @brief Get IPv6 DNS server addresses of an interface
     *
     * @param  itf_guid                     The interface GUID string
     * @return std::vector<std::string>     IPv6 server addresses found
     */
    static std::vector<std::wstring> interface_ipv6_dns_servers(const std::wstring &itf_guid)
    {
        typename REG::Key itf_key(std::wstring(REG::subkey_ipv6_itfs) + L"\\" + itf_guid);

        auto [servers, error] = REG::get_string(itf_key, L"NameServer");
        if (!error && !servers.empty())
        {
            return string::split(servers, ',');
        }

        if (dhcp_enabled_on_itf<REG>(itf_key))
        {
            auto [in6_addrs, error] = REG::get_binary(itf_key, L"Dhcpv6DNSServers");
            if (!error)
            {
                std::vector<std::wstring> addresses;
                size_t in6_addr_count = in6_addrs.size() / sizeof(IN6_ADDR);
                for (size_t i = 0; i < in6_addr_count; ++i)
                {
                    WCHAR ipv6[64];
                    IN6_ADDR *in6_addr = reinterpret_cast<IN6_ADDR *>(in6_addrs.data()) + i;
                    if (::InetNtopW(AF_INET6, in6_addr, ipv6, _countof(ipv6)))
                    {
                        addresses.emplace_back(ipv6);
                    }
                }
                return addresses;
            }
        }

        return {};
    }

    /**
     * @brief Get all the data necessary for excluding local domains from the tunnel
     *
     * This data is only necessary if all the domains are to be resolved through
     * the VPN. To not break resolving local DNS names, we add so called exclude rules
     * to the NRPT for as long as the tunnel persists.
     *
     * @return std::vector<ExcludeRuleData> The data collected to create exclude rules from.
     */
    static std::vector<ExcludeRuleData> collect_exclude_rule_data()
    {
        std::vector<ExcludeRuleData> data;
        typename REG::Key itfs(REG::subkey_ipv4_itfs);
        typename REG::KeyEnumerator itf_guids(itfs);
        for (const auto &itf_guid : itf_guids)
        {
            // Ignore interfaces that are not connected or disabled
            if (!NETAPI::interface_connected(itf_guid))
            {
                continue;
            }

            std::wstring domain = interface_dns_domain<REG>(itf_guid);
            if (domain.empty())
            {
                continue;
            }

            // Get the DNS server addresses for the interface domain
            auto addresses = interface_ipv4_dns_servers(itf_guid);
            const auto addr6 = interface_ipv6_dns_servers(itf_guid);
            addresses.insert(addresses.end(), addr6.begin(), addr6.end());
            if (addresses.empty())
            {
                continue;
            }

            // Add a leading '.' to the domain and convert it to MULTI_SZ
            domain.resize(domain.size() + 3);
            domain.insert(domain.begin(), L'.');
            domain.push_back(L'\0');
            domain.push_back(L'\0');

            data.push_back({domain, addresses});
        }
        return data;
    }

    /**
     * @brief Open the NRPT key to store our rules at
     *
     * There are two places in the registry where NRPT rules can be found, depending
     * on whether group policy rules are used or not. This function tries for the
     * group policy place first and returns the key for the local rules in case it
     * does not exist.
     *
     * @return REG::Key  the opened Registry handle
     */
    static typename REG::Key open_nrpt_base_key()
    {
        typename REG::Key key(REG::gpol_nrpt_subkey);
        if (key.defined())
        {
            return key;
        }
        return typename REG::Key(REG::local_nrpt_subkey);
    }

    /**
     * @brief Return the rule id prefix any rule starts with
     *
     * @return const char*  the prefix string
     */
    static const char *id_prefix()
    {
        static const char prefix[] = "OpenVPNDNSRouting";
        return prefix;
    }

    /**
     * @brief Generate a rule id string
     *
     * @param process_id    the process id used for the rule
     * @param exclude_rule  whether the rule is for an exclude rule
     * @param n             the number of the exclude rule
     * @return std::string  the rule id string
     */
    static std::string gen_rule_id(DWORD process_id, bool exclude_rule, std::uint32_t n)
    {
        std::ostringstream ss;
        ss << id_prefix();
        if (exclude_rule)
        {
            ss << "X-" << n;
        }
        ss << "-" << process_id;
        return ss.str();
    }

  public:
    /**
     * @brief Return a NRPT rule id
     *
     * @param process_id    the process id used for the rule
     * @return std::string  the rule is string
     */
    static inline std::string rule_id(DWORD process_id)
    {
        return gen_rule_id(process_id, false, 0u);
    }

    /**
     * @brief Return a NRPT exclude rule id
     *
     * @param process_id    the process id used for the rule
     * @param n             the number of this rule
     * @return std::string  the rule id string
     */
    static inline std::string exclude_rule_id(DWORD process_id, std::uint32_t n)
    {
        return gen_rule_id(process_id, true, n);
    }
    class ActionCreate : public Action
    {
      public:
        ActionCreate(DWORD process_id,
                     const std::vector<std::string> &domains,
                     const std::vector<std::string> &dns_servers,
                     bool dnssec)
            : process_id_(process_id),
              domains_(domains),
              dns_servers_(dns_servers),
              dnssec_(dnssec)
        {
        }

        /**
         * @brief Apply NRPT data to the registry
         *
         * In case a --dns server has no domains, we fall back to resolving
         * "all domains" with it and install rules excluding the domains
         * found on the system, so local domain names keep working.
         *
         * @param log   where the rules will be logged to
         */
        void execute(std::ostream &log) override
        {
            // Convert domains into a wide MULTI_SZ string
            std::wstring domains;
            if (domains_.empty())
            {
                // --dns options did not specify any domains to resolve.
                domains = L".";
                domains.push_back(L'\0');
                domains.push_back(L'\0');
                create_exclude_rules(process_id_);
            }
            else
            {
                domains = wstring::pack_string_vector(domains_);
            }

            const std::string id = rule_id(process_id_);
            const std::wstring servers = wstring::from_utf8(string::join(dns_servers_, ";"));
            log << to_string() << " id=[" << id << "]" << std::endl;
            create_rule(id, domains, servers, dnssec_);
        }

        /**
         * @brief Produce a textual representating of the NRPT data
         *
         * @return std::string  the data as string
         */
        std::string to_string() const override
        {
            std::ostringstream os;
            os << "NRPT::ActionCreate"
               << " pid=[" << process_id_ << "]"
               << " domains=[" << string::join(domains_, ",") << "]"
               << " dns_servers=[" << string::join(dns_servers_, ",") << "]"
               << " dnssec=[" << dnssec_ << "]";
            return os.str();
        }

      private:
        DWORD process_id_;
        const std::vector<std::string> domains_;
        const std::vector<std::string> dns_servers_;
        const bool dnssec_;
    };

    class ActionDelete : public Action
    {
      public:
        ActionDelete(DWORD process_id)
            : process_id_(process_id)
        {
        }

        /**
         * @brief Delete all rules this process has set.
         *
         * Note that the ActionCreate and ActionDelete must be
         * executed from the same process for this to work reliably
         *
         * @param log   where the log message goes
         */
        void execute(std::ostream &log) override
        {
            log << to_string() << std::endl;
            delete_rules(process_id_);
        }

        /**
         * @brief Return the log message
         *
         * @return std::string
         */
        std::string to_string() const override
        {
            std::ostringstream ss;
            ss << "NRPT::ActionDelete pid=[" << process_id_ << "]";
            return ss.str();
        }

      protected:
        DWORD process_id_;
    };
};

using NRPT = Nrpt<Win::Reg, Win::NetApi>;

} // namespace openvpn::TunWin
