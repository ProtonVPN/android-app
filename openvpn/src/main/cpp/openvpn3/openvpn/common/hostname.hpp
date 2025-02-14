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

// Get hostname

#ifndef OPENVPN_COMMON_HOSTNAME_H
#define OPENVPN_COMMON_HOSTNAME_H

#include <string>

#ifdef USE_ASIO
#include <asio/ip/host_name.hpp>
#endif

namespace openvpn {
inline std::string get_hostname()
{
#ifdef USE_ASIO
    return asio::ip::host_name();
#else
    return "HOSTNAME_UNDEFINED";
#endif
}
} // namespace openvpn

#endif
