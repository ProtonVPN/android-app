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

#ifndef OPENVPN_COMMON_ARRAYSIZE_H
#define OPENVPN_COMMON_ARRAYSIZE_H

#include <cstddef> // defines size_t

namespace openvpn {
template <typename T, std::size_t N>
constexpr std::size_t array_size(T (&)[N])
{
    return N;
}
} // namespace openvpn

#endif
