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

// For debugging, reduce effective buffer size for I/O.
// Enable by defining OPENVPN_BUF_CLAMP_READ and/or OPENVPN_BUF_CLAMP_WRITE

#ifndef OPENVPN_BUFFER_BUFCLAMP_H
#define OPENVPN_BUFFER_BUFCLAMP_H

#include <algorithm>

#include <openvpn/common/size.hpp>

namespace openvpn {
inline size_t buf_clamp_read(const size_t size)
{
#ifdef OPENVPN_BUF_CLAMP_READ
    return std::min(size, size_t(OPENVPN_BUF_CLAMP_READ));
#else
    return size;
#endif
}

inline size_t buf_clamp_write(const size_t size)
{
#ifdef OPENVPN_BUF_CLAMP_WRITE
    return std::min(size, size_t(OPENVPN_BUF_CLAMP_WRITE));
#else
    return size;
#endif
}
} // namespace openvpn

#endif
