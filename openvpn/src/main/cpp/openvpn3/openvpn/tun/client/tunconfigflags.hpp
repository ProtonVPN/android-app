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
struct TunConfigFlags
{
    enum
    {
        ADD_BYPASS_ROUTES = (1 << 0),
        DISABLE_IFACE_UP = (1 << 1),
        DISABLE_REROUTE_GW = (1 << 2),
    };
};
} // namespace openvpn
