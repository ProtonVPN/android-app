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

// OpenSSL specific methods for TLS version

#pragma once

#include <openvpn/ssl/tlsver.hpp>

namespace openvpn::TLSVersion {

inline int toTLSVersion(const Type version)
{

    switch (version)
    {
    case Type::UNDEF:
    default:
        return 0;
    case Type::V1_0:
        return TLS1_VERSION;
    case Type::V1_1:
        return TLS1_1_VERSION;
    case Type::V1_2:
        return TLS1_2_VERSION;
    case Type::V1_3:
#ifdef TLS1_3_VERSION
        return TLS1_3_VERSION;
#else
        // TLS 1.3 is SSL 3.4
        return 0x0304;
#endif
    }
}
} // namespace openvpn::TLSVersion
