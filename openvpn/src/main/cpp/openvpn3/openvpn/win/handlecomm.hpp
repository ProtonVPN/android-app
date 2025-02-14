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

#include <windows.h>

#include <openvpn/buffer/bufhex.hpp>
#include <openvpn/win/winerr.hpp>

namespace openvpn::Win::HandleComm {

OPENVPN_EXCEPTION(handle_comm);

// Duplicate a local handle into the address space of a
// remote process and return as a hex string that can be
// communicated across a process boundary.
inline std::string send_handle(const HANDLE handle,
                               const HANDLE remote_process)
{
    HANDLE remote_handle;
    if (!::DuplicateHandle(GetCurrentProcess(),
                           handle,
                           remote_process,
                           &remote_handle,
                           0,
                           FALSE,
                           DUPLICATE_SAME_ACCESS))
    {
        const Win::LastError err;
        OPENVPN_THROW(handle_comm, "send_handle: DuplicateHandle failed: " << err.message());
    }
    return BufHex::render(remote_handle);
}

// Duplicate a remote handle (specified as a hex string) into
// the address space of the local process.
inline HANDLE receive_handle(const std::string &remote_handle_hex,
                             const HANDLE remote_process)
{
    const HANDLE remote_handle = BufHex::parse<HANDLE>(remote_handle_hex, "receive_handle");
    HANDLE local_handle;
    if (!::DuplicateHandle(remote_process,
                           remote_handle,
                           GetCurrentProcess(),
                           &local_handle,
                           0,
                           FALSE,
                           DUPLICATE_SAME_ACCESS))
    {
        const Win::LastError err;
        OPENVPN_THROW(handle_comm, "receive_handle: DuplicateHandle failed: " << err.message());
    }
    return local_handle;
}

} // namespace openvpn::Win::HandleComm
