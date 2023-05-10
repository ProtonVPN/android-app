//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2022 OpenVPN Inc.
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU Affero General Public License Version 3
//    as published by the Free Software Foundation.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU Affero General Public License for more details.
//
//    You should have received a copy of the GNU Affero General Public License
//    along with this program in the COPYING file.
//    If not, see <http://www.gnu.org/licenses/>.

// OpenSSL specific methods for TLS version

#pragma once

#include <openvpn/ssl/tlsver.hpp>

namespace openvpn {
namespace TLSVersion {

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
} // namespace TLSVersion
} // namespace openvpn
