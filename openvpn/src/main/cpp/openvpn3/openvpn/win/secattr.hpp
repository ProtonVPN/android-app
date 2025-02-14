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

#pragma once

// windows SECURITY_ATTRIBUTES utilities

#include <windows.h>
#include <sddl.h>

#include <openvpn/common/exception.hpp>
#include <openvpn/win/winerr.hpp>

namespace openvpn::Win {

struct SecurityAttributes
{
    OPENVPN_EXCEPTION(win_sec_attr);

    SecurityAttributes(const std::string &sddl_string,
                       const bool inherit,
                       const std::string &title)
    {
        sa.nLength = sizeof(SECURITY_ATTRIBUTES);
        sa.bInheritHandle = inherit ? TRUE : FALSE;
        sa.lpSecurityDescriptor = nullptr;
        if (!sddl_string.empty())
        {
            if (!::ConvertStringSecurityDescriptorToSecurityDescriptorA(
                    sddl_string.c_str(),
                    SDDL_REVISION_1,
                    &sa.lpSecurityDescriptor, // allocates memory
                    NULL))
            {
                const Win::LastError err;
                OPENVPN_THROW(win_sec_attr, "failed to create security descriptor for " << title << " : " << err.message());
            }
        }
    }

    ~SecurityAttributes()
    {
        ::LocalFree(sa.lpSecurityDescriptor);
    }

    SECURITY_ATTRIBUTES sa;
};

} // namespace openvpn::Win
