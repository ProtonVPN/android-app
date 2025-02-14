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


// Windows network related utilities

#pragma once

#include <string>
#include "openvpn/win/reg.hpp"

namespace openvpn::Win {

struct NetApi
{
    /**
     * Get the string interface UUID (with braces) for an interface alias name
     *
     * @param  itf_name         the interface alias name
     * @return std::wstring     the wide IID string
     */
    static std::wstring get_itf_id(const std::string &itf_name)
    {
        DWORD err;
        NET_LUID luid;
        err = ::ConvertInterfaceAliasToLuid(wstring::from_utf8(itf_name).c_str(), &luid);
        if (err)
            return {};

        IID guid;
        err = ::ConvertInterfaceLuidToGuid(&luid, &guid);
        if (err)
            return {};

        PWSTR iid_str;
        if (::StringFromIID(guid, &iid_str) != S_OK)
            return {};

        std::wstring iid = iid_str;
        ::CoTaskMemFree(iid_str);
        return iid;
    }

    /**
     * @brief Check if an interface is connected and up
     *
     * @param  iid_str  the interface GUID string
     * @return bool     true if the interface is connected and up,
     *                  false otherwise or in case an error happened
     */
    static bool interface_connected(const std::wstring &iid_str)
    {
        GUID iid;
        MIB_IF_ROW2 itf_row;

        // Get LUID from GUID string
        if (::IIDFromString(iid_str.c_str(), &iid) != S_OK
            || ::ConvertInterfaceGuidToLuid(&iid, &itf_row.InterfaceLuid) != NO_ERROR)
        {
            return false;
        }

        // Look up interface status
        if (::GetIfEntry2(&itf_row) != NO_ERROR)
        {
            return false;
        }

        // Check if interface is connected and up
        if (itf_row.MediaConnectState != MediaConnectStateConnected
            || itf_row.OperStatus != IfOperStatusUp)
        {
            return false;
        }

        return true;
    }
};

/**
 * @brief Read interface specific domain suffix
 *
 * It can be either be the one assigned by DHCP or manually.
 *
 * @tparam REG          the Registry abstraction to use (default: Win::Reg)
 * @param  itf_guid     The interface GUID string
 * @return std::wstring The domain found, or the empty string
 */
template <typename REG = Win::Reg>
static std::wstring interface_dns_domain(const std::wstring &itf_guid)
{
    typename REG::Key itf_key(std::wstring(REG::subkey_ipv4_itfs) + L"\\" + itf_guid);

    auto [domain, error] = REG::get_string(itf_key, L"DhcpDomain");
    if (!error && !domain.empty())
    {
        return domain;
    }
    else
    {
        auto [domain, error] = REG::get_string(itf_key, L"Domain");
        if (!error && !domain.empty())
        {
            return domain;
        }
    }

    return {};
}

/**
 * @brief Checks if DHCP is enabled for an interface
 *
 * @tparam REG      the Registry abstraction to use (default: Win::Reg)
 * @param  itf_key  REG::Key of the interface to check for
 * @return bool     true if DHCP is enabled, false if
 *                  disabled or an error occurred
 */
template <typename REG = Win::Reg>
static bool dhcp_enabled_on_itf(typename REG::Key &itf_key)
{
    auto [dhcp, error] = REG::get_dword(itf_key, L"EnableDHCP");
    return !error && dhcp ? true : false;
}

} // namespace openvpn::Win
