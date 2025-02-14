//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2024- OpenVPN Inc.
//
//    SPDX-License-Identifier: MPL-2.0 OR AGPL-3.0-only WITH openvpn3-openssl-exception
//

//

/**
 * @brief DNS utilities for Windows
 *
 * DNS search suffixes are applied to not fully qualified domain names
 * before lookup, e.g. you try to resolve 'host' and Windows completes
 * this to host.searchdomain1.in and host.searchdomain-n.com and looks
 * up these two FQDNs.
 *
 * The domain suffixes for completion can be configured in various ways
 * in Windows. There are so called adapter domain suffixes which can be
 * specified with each network adapter configuration. However, these are
 * overridden by a so called search list, which is shared between all
 * adapters. If you want to have more than one search suffix defined for
 * an adapter you have to use a search list, otherwise the primary suffix
 * is enough. In addition to that a search list can also be defined by
 * a group policy, which overrides both previous settings. The local and
 * group polixy search lists a located in different subkeys in the Registry.
 * There's also a primary domain suffix, which is for the Windows AD Domain.
 *
 * OpenVPN clients will apply pushed search domains this way:
 *  - If it is a single domain it will be added as primary domain suffix,
 *    unless there is a search list defined already. In that case the
 *    domain is added to the search list.
 *  - If there are multiple domains pushed and there already is a search
 *    list defined, the pushed domains will be added to the list. Otherwise
 *    a new serach list will be created. This newly created search list
 *    will also include the primary domain and all adapter domains, so that
 *    lookup of unqualified names continues to work when the VPN is
 *    connected.
 */

#pragma once

#include <array>

#include <winternl.h>

#include <openvpn/common/wstring.hpp>
#include <openvpn/common/action.hpp>
#include <openvpn/win/reg.hpp>
#include <openvpn/win/netutil.hpp>

namespace openvpn::TunWin {

/**
 * @brief Manage DNS search suffixes for Windows
 *
 * @tparam REG      Registry abstraction class to use
 * @tparam NETAPI   Network related Win32 API class to use
 */
template <typename REG, typename NETAPI>
class Dns
{
    /**
     * Registry locations the DNS search domains list can be stored in.
     * When the first key exists and it has domains in the "SearchList" value,
     * then these GPO provided domains will be used as suffixes, otherwise the
     * manually created ones in the second key will be used (if they exist).
     */
    static constexpr std::array<PCWSTR, 2> searchlist_subkeys{
        LR"(SOFTWARE\Policies\Microsoft\WindowsNT\DNSClient)",
        LR"(System\CurrentControlSet\Services\TCPIP\Parameters)"};

    /**
     * @brief Return the Key for the DNS domain "SearchList" value
     *
     * It also returns a boolean value, telling whether a SearchList already exists
     * under the returned registry key.
     *
     * @return std::pair<REG::Key, bool>   the search list key and a
     *                                     boolean telling if a list exists
     */
    static std::pair<typename REG::Key, bool> open_searchlist_key()
    {
        for (const auto subkey : searchlist_subkeys)
        {
            typename REG::Key key(subkey);
            if (key.defined())
            {
                auto [list, error] = REG::get_string(key, L"SearchList");
                if (!error && !list.empty())
                {
                    return {std::move(key), true};
                }
                else if (subkey == searchlist_subkeys.back())
                {
                    // Return the local subkey (last in the list) as default
                    return {std::move(key), false};
                }
            }
        }
        return {typename REG::Key{}, false};
    }

    /**
     * @brief Check if a initial list had already been created
     *
     * @param  key   key to look under for the initial list
     * @return bool  to indicate if the initial list is already present
     */
    static bool initial_searchlist_exists(typename REG::Key &key)
    {
        auto [value, error] = REG::get_string(key, L"InitialSearchList");
        return error ? false : true;
    }

    /**
     * Prepare DNS domain "SearchList" registry value, so additional
     * VPN domains can be added and its original state can be restored
     * when the VPN disconnects.
     *
     * @param  key          the registry key to modify values under
     * @param  searchlist   string of comma separated domains to use as the list
     *
     * @return bool      indicates whether the list is stored successfully
     */
    static bool set_initial_searchlist(typename REG::Key &key, const std::wstring &searchlist)
    {
        LSTATUS err;
        err = REG::set_string(key, L"InitialSearchList", searchlist);
        if (err)
        {
            return false;
        }
        err = REG::set_string(key, L"SearchList", searchlist);
        if (err)
        {
            return false;
        }
        return true;
    }

    /**
     * @brief Set the initial searchlist from the existing search list
     *
     * @param key       the registry key to modify values under
     * @return bool     indicates whether the list is stored successfully
     */
    static bool set_initial_searchlist_from_existing(typename REG::Key &key)
    {
        auto [searchlist, error] = REG::get_string(key, L"SearchList");
        if (error)
        {
            return false;
        }

        /* Store a copy of the original list */
        LSTATUS err = REG::set_string(key, L"OriginalSearchList", searchlist);
        if (err)
        {
            return false;
        }

        return set_initial_searchlist(key, searchlist);
    }

    /**
     * Create a initial DNS search list if it does not exist already
     *
     * @param key       the registry key to modify values under
     * @return bool     to indicate creation success or failure
     */
    static bool set_initial_searchlist_from_domains(typename REG::Key &key)
    {
        std::wstring list;

        {
            // Add primary domain to the list, if exists
            typename REG::Key tcpip_params(LR"(SYSTEM\CurrentControlSet\Services\Tcpip\Parameters)");
            auto [domain, error] = REG::get_string(tcpip_params, L"Domain");
            if (!error && !domain.empty())
            {
                list.append(domain);
            }
        }

        typename REG::Key itfs(REG::subkey_ipv4_itfs);
        typename REG::KeyEnumerator itf_guids(itfs);
        for (const auto &itf_guid : itf_guids)
        {
            // Ignore interfaces that are not connected or disabled
            if (!NETAPI::interface_connected(itf_guid))
            {
                continue;
            }

            // Get the DNS domain for routing
            std::wstring domain = interface_dns_domain<REG>(itf_guid);
            if (domain.empty())
            {
                continue;
            }

            // FIXME: expand domain if "UseDomainNameDevolution" is set?
            if (list.size())
            {
                list.append(L",");
            }
            list.append(domain);
        }

        return set_initial_searchlist(key, list);
    }

    /**
     * @brief Set interface specific domain suffix
     *
     * @param  itf_name   interface alias name to set the domain suffix for
     * @param  domain     domain suffix to set as wide character string
     *
     * @return bool     to indicate success or failure setting the domain suffix
     */
    static bool set_itf_domain_suffix(const std::string &itf_name, const std::wstring &domain)
    {
        const std::wstring iid = NETAPI::get_itf_id(itf_name);
        if (iid.empty())
        {
            return false;
        }

        typename REG::Key itf_key(std::wstring(REG::subkey_ipv4_itfs) + L"\\" + iid);
        PCWSTR name = dhcp_enabled_on_itf<REG>(itf_key) ? L"DhcpDomain" : L"Domain";
        LSTATUS err = REG::set_string(itf_key, name, domain);
        if (err)
        {
            return false;
        }

        return true;
    }

    /**
     * @brief Append domain suffixes to an existing search list
     *
     * @param  key      the registry key to modify values under
     * @param  domains  domain suffixes as comma separated string
     *
     * @return bool     to indicate success or failure
     */
    static bool add_to_searchlist(typename REG::Key &key, const std::wstring &domains)
    {
        auto [list, error] = REG::get_string(key, L"SearchList");
        if (error)
        {
            return false;
        }
        if (list.size())
        {
            list.append(L",");
        }
        list.append(domains);

        LSTATUS err = REG::set_string(key, L"SearchList", list);
        return err ? false : true;
    }

  public:
    OPENVPN_EXCEPTION(dns_error);

    /**
     * @brief Add DNS search domain(s)
     *
     * Extend the list of DNS search domains present on the system.
     * If domains is only a single domain (no comma) and there currently is
     * no search list defined on the system, a interface specific domain
     * suffix is used instead of generating a new search list.
     *
     * @param  itf_name   alias name of the interface a single domain is set for
     * @param  domains    a comma separated list of domain names
     *
     */
    static void set_search_domains(const std::string &itf_name, const std::string &domains)
    {
        if (domains.empty())
        {
            return;
        }

        auto [list_key, list_exists] = open_searchlist_key();
        bool initial_list_exists = initial_searchlist_exists(list_key);
        bool single_domain = domains.find(',') == domains.npos;
        if (!initial_list_exists)
        {
            if (list_exists)
            {
                if (!set_initial_searchlist_from_existing(list_key))
                {
                    return;
                }
            }
            else if (!single_domain)
            {
                if (!set_initial_searchlist_from_domains(list_key))
                {
                    return;
                }
            }
        }

        std::wstring wide_domains = wstring::from_utf8(domains);
        bool success_adding = single_domain && !list_exists
                                  ? set_itf_domain_suffix(itf_name, wide_domains)
                                  : add_to_searchlist(list_key, wide_domains);
        if (!success_adding)
        {
            remove_search_domains(itf_name, domains);
        }
    }

    /**
     * @brief Reset the DNS "SearchList" to its original value
     *
     * Looks for "OriginalSearchList" value as the one to reset to. If it doesn't
     * exists resets to the empty value, which is interpreted as no search list.
     *
     * @param list_key      key in the registry to reset the values under
     */
    static void reset_search_domains(typename REG::Key &list_key)
    {
        auto [originallist, error] = REG::get_string(list_key, L"OriginalSearchList");
        if (!error || error == ERROR_FILE_NOT_FOUND)
        {
            // Restore the original search list or set a empty list
            REG::set_string(list_key, L"SearchList", originallist);
        }

        REG::delete_value(list_key, L"InitialSearchList");
        REG::delete_value(list_key, L"OriginalSearchList");
    }

    /**
     * @brief Remove domain suffix(es) from the system
     *
     * If a search list exists, it is restored to the previous state.
     * The adapter domain suffix is also emptied. And temporary values
     * from the registry are removed if they are no longer needed.
     *
     * @param  itf_name   alias name of the interface the suffix is removed from
     * @param  domains    a comma separated list of domain names to be removed
     */
    static void remove_search_domains(const std::string &itf_name, const std::string &domains)
    {
        if (domains.empty())
        {
            return;
        }

        set_itf_domain_suffix(itf_name, {});

        auto [list_key, list_exists] = open_searchlist_key();
        if (list_exists)
        {
            auto [searchlist, error] = REG::get_string(list_key, L"SearchList");
            if (error)
            {
                return;
            }

            // Remove domains from list
            const std::wstring wdomains = wstring::from_utf8(domains);
            auto pos = searchlist.find(wdomains);
            if (pos != searchlist.npos)
            {
                // Domains are in the search list, remove them
                if (searchlist.size() == wdomains.size())
                {
                    // No other domains in the list
                    searchlist.clear();
                }
                else if (pos == 0)
                {
                    // Also remove trailing comma
                    searchlist.erase(pos, wdomains.size() + 1);
                }
                else
                {
                    // Also remove leading comma
                    searchlist.erase(pos - 1, wdomains.size() + 1);
                }
            }

            auto [initiallist, err] = REG::get_string(list_key, L"InitialSearchList");
            if (err)
            {
                return;
            }

            // Compare shortened list with initial list
            if (searchlist == initiallist)
            {
                // Reset everything to the original state
                reset_search_domains(list_key);
            }
            else
            {
                // Store the shortened search list
                REG::set_string(list_key, L"SearchList", searchlist);
            }
        }
    }


    class ActionCreate : public Action
    {
      public:
        ActionCreate(const std::string &itf_name, const std::string &search_domains)
            : itf_name_(itf_name), search_domains_(search_domains)
        {
        }

        /**
         * @brief Apply DNS data to the system
         *
         * @param log   where the rules will be logged to
         */
        void execute(std::ostream &log) override
        {
            log << to_string() << std::endl;
            set_search_domains(itf_name_, search_domains_);
        }

        /**
         * @brief Produce a textual representating of the DNS data
         *
         * @return std::string  the data as string
         */
        std::string to_string() const override
        {
            std::ostringstream os;
            os << "DNS::ActionCreate"
               << " interface name=[" << itf_name_ << "]"
               << " search domains=[" << search_domains_ << "]";
            return os.str();
        }

      private:
        const std::string itf_name_;
        const std::string search_domains_;
    };

    class ActionDelete : public Action
    {
      public:
        ActionDelete(const std::string &itf_name, const std::string &search_domains)
            : itf_name_(itf_name), search_domains_(search_domains)
        {
        }

        /**
         * @brief Undo any modification to the DNS settings.
         *
         * @param log   where the log message goes
         */
        void execute(std::ostream &log) override
        {
            log << to_string() << std::endl;
            remove_search_domains(itf_name_, search_domains_);
        }

        /**
         * @brief Return the log message
         *
         * @return std::string
         */
        std::string to_string() const override
        {
            std::ostringstream ss;
            ss << "DNS::ActionDelete"
               << " interface name=[" << itf_name_ << "]"
               << " search domains=[" << search_domains_ << "]";
            return ss.str();
        }

      private:
        const std::string itf_name_;
        const std::string search_domains_;
    };

    class ActionApply : public Action
    {
      public:
        /**
         * @brief Apply any modification to the DNS settings by signaling the resolver.
         *
         * @param log   where the log message goes
         */
        void execute(std::ostream &log) override
        {
            const char *gpol_status = "";
            Reg::Key gpol_nrpt_key(Reg::gpol_nrpt_subkey);
            if (gpol_nrpt_key.defined())
            {
                gpol_status = apply_gpol_nrtp_rules() ? " [gpol successful]" : " [gpol failed]";
            }

            const char *status = apply_dns_settings() ? "successful" : "failed";
            log << to_string() << ": " << status << gpol_status << std::endl;
        }

        /**
         * @brief Return the log message
         *
         * @return std::string
         */
        std::string to_string() const override
        {
            return "DNS::ActionApply";
        }

      private:
        /**
         * @brief Signal the DNS resolver to reload its settings
         *
         * @return bool to indicate if the reload was initiated
         */
        bool apply_dns_settings()
        {
            bool res = false;
            SC_HANDLE scm = static_cast<SC_HANDLE>(INVALID_HANDLE_VALUE);
            SC_HANDLE dnssvc = static_cast<SC_HANDLE>(INVALID_HANDLE_VALUE);

            scm = ::OpenSCManager(nullptr, nullptr, SC_MANAGER_ALL_ACCESS);
            if (scm == nullptr)
            {
                goto out;
            }

            dnssvc = ::OpenServiceA(scm, "Dnscache", SERVICE_PAUSE_CONTINUE);
            if (dnssvc == nullptr)
            {
                goto out;
            }

            SERVICE_STATUS status;
            if (::ControlService(dnssvc, SERVICE_CONTROL_PARAMCHANGE, &status) == 0)
            {
                goto out;
            }

            res = true;

        out:
            if (dnssvc != INVALID_HANDLE_VALUE)
            {
                ::CloseServiceHandle(dnssvc);
            }
            if (scm != INVALID_HANDLE_VALUE)
            {
                ::CloseServiceHandle(scm);
            }
            return res;
        }

        /**
         * @brief Signal the DNS resolver (and others potentially) to reload the NRTP rules group policy settings
         *
         * @return bool to indicate if the reload was initiated
         */
        bool apply_gpol_nrtp_rules()
        {
            SYSTEM_INFO si;
            ::GetSystemInfo(&si);
            const bool win_32bit = si.wProcessorArchitecture == PROCESSOR_ARCHITECTURE_INTEL;

            return win_32bit ? apply_gpol_nrtp_rules_32() : apply_gpol_nrtp_rules_64();
        }

        /**
         * @brief Signal the DNS resolver (and others potentially) to reload the
         *        NRTP rules group policy settings on 32 bit Windows systems
         *
         * @return bool to indicate if the reload was initiated
         */
        bool apply_gpol_nrtp_rules_32()
        {
            using publish_fn_t = NTSTATUS(__stdcall *)(
                DWORD StateNameLo,
                DWORD StateNameHi,
                DWORD TypeId,
                DWORD Buffer,
                DWORD Length,
                DWORD ExplicitScope);
            publish_fn_t RtlPublishWnfStateData;
            constexpr DWORD WNF_GPOL_SYSTEM_CHANGES_HI = 0x0D891E2A;
            constexpr DWORD WNF_GPOL_SYSTEM_CHANGES_LO = 0xA3BC0875;

            HMODULE ntdll = ::LoadLibraryA("ntdll.dll");
            if (ntdll == nullptr)
                return false;

            RtlPublishWnfStateData = reinterpret_cast<publish_fn_t>(::GetProcAddress(ntdll, "RtlPublishWnfStateData"));
            if (RtlPublishWnfStateData == nullptr)
                return false;

            if (RtlPublishWnfStateData(WNF_GPOL_SYSTEM_CHANGES_LO, WNF_GPOL_SYSTEM_CHANGES_HI, 0, 0, 0, 0) != ERROR_SUCCESS)
                return false;

            return true;
        }

        /**
         * @brief Signal the DNS resolver (and others potentially) to reload the
         *        NRTP rules group policy settings on 64 bit Windows systems
         *
         * @return bool to indicate if the reload was initiated
         */
        bool apply_gpol_nrtp_rules_64()
        {
            using publish_fn_t = NTSTATUS (*)(
                __int64 StateName,
                __int64 TypeId,
                __int64 Buffer,
                unsigned int Length,
                __int64 ExplicitScope);
            publish_fn_t RtlPublishWnfStateData;
            constexpr INT64 WNF_GPOL_SYSTEM_CHANGES = 0x0D891E2AA3BC0875;

            HMODULE ntdll = ::LoadLibraryA("ntdll.dll");
            if (ntdll == nullptr)
                return false;

            RtlPublishWnfStateData = reinterpret_cast<publish_fn_t>(::GetProcAddress(ntdll, "RtlPublishWnfStateData"));
            if (RtlPublishWnfStateData == nullptr)
                return false;

            if (RtlPublishWnfStateData(WNF_GPOL_SYSTEM_CHANGES, 0, 0, 0, 0) != ERROR_SUCCESS)
                return false;

            return true;
        }
    };
};

using DNS = Dns<Win::Reg, Win::NetApi>;

} // namespace openvpn::TunWin
