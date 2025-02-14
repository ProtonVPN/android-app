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

// proxy settings for Windows

#pragma once

#include <wininet.h>

#include <openvpn/win/impersonate.hpp>
#include <openvpn/tun/proxy.hpp>
#include <openvpn/win/reg.hpp>

using namespace openvpn::Win;

namespace openvpn::TunWin {
class WinProxySettings : public ProxySettings
{
  public:
    typedef RCPtr<WinProxySettings> Ptr;

    WinProxySettings(const TunBuilderCapture::ProxyAutoConfigURL &config_arg)
        : ProxySettings(config_arg)
    {
    }

    void set_proxy(bool del) override
    {
        Impersonate imp{false};

        LONG status;
        Reg::Key hkcu;
        Reg::Key key;

        status = ::RegOpenCurrentUser(KEY_QUERY_VALUE | KEY_SET_VALUE, hkcu.ref());
        check_reg_error<proxy_error>(status, "RegOpenCurrentUser");

        status = ::RegCreateKeyExA(hkcu(), key_name, 0, NULL, 0, KEY_QUERY_VALUE | KEY_SET_VALUE, NULL, key.ref(), NULL);
        check_reg_error<proxy_error>(status, key_name);

        if (!del)
        {
            save_key(key, "AutoConfigURL", config.url, true);
            save_key(key, "ProxyEnable", "0", false);
        }
        else
        {
            restore_key(key, "AutoConfigURL", true);
            restore_key(key, "ProxyEnable", false);
        }

        // WinInet API cannot be called from service, even via impersonation
        if (!imp.is_local_system())
        {
            OPENVPN_LOG("Refresh proxy settings");

            InternetSetOptionA(NULL, INTERNET_OPTION_SETTINGS_CHANGED, NULL, 0);
            InternetSetOptionA(NULL, INTERNET_OPTION_REFRESH, NULL, 0);
        }
    }

  private:
    void restore_key(Win::Reg::Key &regkey, const std::string &key, bool str)
    {
        LONG status;
        char prev_val_str[1024] = {0}; // should be enough to fit proxy URL
        DWORD prev_val_dword;
        DWORD prev_buf_size = str ? sizeof(prev_val_str) : sizeof(prev_val_dword);
        bool del = false;
        Win::Reg::Key hkcu;

        status = ::RegOpenCurrentUser(KEY_QUERY_VALUE | KEY_SET_VALUE, hkcu.ref());
        check_reg_error<proxy_error>(status, "RegOpenCurrentUser");

        // get previous value
        std::string prev_key_name = sname + key;
        status = ::RegGetValueA(hkcu(),
                                key_name,
                                prev_key_name.c_str(),
                                str ? RRF_RT_REG_SZ : RRF_RT_REG_DWORD,
                                NULL,
                                str ? (PVOID)prev_val_str : (PVOID)&prev_val_dword,
                                &prev_buf_size);
        check_reg_error<proxy_error>(status, prev_key_name);

        RegDeleteValueA(regkey(), prev_key_name.c_str());

        // check if previous value needs to be deleted
        if (str)
            del = strcmp(delete_value_str, prev_val_str) == 0;
        else
            del = prev_val_dword == delete_value_dword;

        if (del)
            ::RegDeleteValueA(regkey(), key.c_str());
        else
            ::RegSetValueExA(regkey(),
                             key.c_str(),
                             0,
                             str ? REG_SZ : REG_DWORD,
                             str ? reinterpret_cast<const BYTE *>(prev_val_str) : reinterpret_cast<CONST BYTE *>(&prev_val_dword),
                             str ? static_cast<DWORD>(strlen(prev_val_str) + 1) : sizeof(prev_val_dword));
    }

    void save_key(Win::Reg::Key &regkey, const std::string &key, const std::string &value, bool str)
    {
        LONG status;
        char prev_val_str[1024] = {0}; // should be enought to fit proxy URL
        DWORD prev_val_dword;
        DWORD prev_buf_size = str ? sizeof(prev_val_str) : sizeof(prev_val_dword);
        Win::Reg::Key hkcu;

        status = ::RegOpenCurrentUser(KEY_QUERY_VALUE | KEY_SET_VALUE, hkcu.ref());
        check_reg_error<proxy_error>(status, "RegOpenCurrentUser");

        // get original value
        status = ::RegGetValueA(hkcu(),
                                key_name,
                                key.c_str(),
                                str ? RRF_RT_REG_SZ : RRF_RT_REG_DWORD,
                                NULL,
                                str ? (PVOID)prev_val_str : (PVOID)&prev_val_dword,
                                &prev_buf_size);
        switch (status)
        {
        case ERROR_FILE_NOT_FOUND:
            // mark that original value doesn't exist
            strcpy(prev_val_str, delete_value_str);
            prev_val_dword = delete_value_dword;
        case ERROR_SUCCESS:
            break;
        default:
            check_reg_error<proxy_error>(status, key);
            break;
        }

        // save original value
        std::string prev_key_name = sname + key;
        status = ::RegSetValueExA(regkey(),
                                  prev_key_name.c_str(),
                                  0,
                                  str ? REG_SZ : REG_DWORD,
                                  str ? reinterpret_cast<const BYTE *>(prev_val_str) : reinterpret_cast<CONST BYTE *>(&prev_val_dword),
                                  str ? static_cast<DWORD>(strlen(prev_val_str) + 1) : sizeof(prev_val_dword));
        check_reg_error<proxy_error>(status, prev_key_name);

        // save new value
        DWORD val_dword = 0;
        if (!str)
            val_dword = std::atol(value.c_str());
        status = ::RegSetValueExA(regkey(),
                                  key.c_str(),
                                  0,
                                  str ? REG_SZ : REG_DWORD,
                                  str ? reinterpret_cast<const BYTE *>(value.c_str()) : reinterpret_cast<CONST BYTE *>(&val_dword),
                                  str ? static_cast<DWORD>(value.length() + 1) : sizeof(val_dword));
        check_reg_error<proxy_error>(status, key);
    }

    const char *key_name = "Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings";
    const char *delete_value_str = "DeleteValue";
    const DWORD delete_value_dword = 0xCAFEBABE;
};
} // namespace openvpn::TunWin
