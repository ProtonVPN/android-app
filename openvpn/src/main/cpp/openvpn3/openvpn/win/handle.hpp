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

// windows HANDLE utilities

#ifndef OPENVPN_WIN_HANDLE_H
#define OPENVPN_WIN_HANDLE_H

#include <windows.h>

namespace openvpn::Win::Handle {
inline HANDLE undefined()
{
    return INVALID_HANDLE_VALUE;
}

inline bool defined(HANDLE handle)
{
    return handle != nullptr && handle != INVALID_HANDLE_VALUE;
}
} // namespace openvpn::Win::Handle

#endif
