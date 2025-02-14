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

// windows SECURITY_ATTRIBUTES utilities

#ifndef OPENVPN_WIN_MODNAME_H
#define OPENVPN_WIN_MODNAME_H

#include <windows.h>

#include <string>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/wstring.hpp>
#include <openvpn/win/winerr.hpp>
#include <openvpn/win/reg.hpp>
#include <openvpn/win/npinfo.hpp>

namespace openvpn::Win {

inline std::wstring module_name()
{
    // get path to our binary
    wchar_t path[MAX_PATH];
    if (!::GetModuleFileNameW(NULL, path, MAX_PATH))
    {
        const Win::LastError err;
        OPENVPN_THROW_EXCEPTION("GetModuleFileNameW failed: " << err.message());
    }
    return std::wstring(path);
}

inline std::string module_name_utf8()
{
    return wstring::to_utf8(module_name());
}

inline std::string omiclient_path()
{
    char strbuf[256] = {0};
    DWORD len = sizeof(strbuf);
    DWORD data_type;
    auto status = ::RegGetValueA(HKEY_LOCAL_MACHINE,
                                 "SOFTWARE\\OpenVPN",
                                 "omi_exe_path",
                                 RRF_RT_REG_SZ,
                                 &data_type,
                                 (LPBYTE)strbuf,
                                 &len);

    if (status != ERROR_SUCCESS)
    {
        const Win::Error err(status);
        OPENVPN_THROW_EXCEPTION("Cannot read HKLM\\SOFTWARE\\OpenVPN\\omi_exe_path: " << err.message());
    }

    return strbuf;
}
} // namespace openvpn::Win

#endif
