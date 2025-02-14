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

// Windows error utilities

#ifndef OPENVPN_WIN_WINERR_H
#define OPENVPN_WIN_WINERR_H

#include <windows.h>

#include <openvpn/io/io.hpp>

namespace openvpn::Win {
struct Error : public openvpn_io::error_code
{
    Error(const DWORD err)
        : openvpn_io::error_code(err, openvpn_io::error::get_system_category())
    {
    }
};

struct LastError : public Error
{
    LastError()
        : Error(::GetLastError())
    {
    }
};
} // namespace openvpn::Win

#endif
