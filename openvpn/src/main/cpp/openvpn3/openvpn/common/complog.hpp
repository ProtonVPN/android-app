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

namespace openvpn {
inline void log_compress(const std::string prefix, const size_t orig_size, const size_t new_size)
{
    OPENVPN_LOG(prefix
                << ' ' << orig_size
                << " -> " << new_size
                << " -- compression ratio: " << double(orig_size) / double(new_size));
}
} // namespace openvpn
