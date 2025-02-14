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

// private key types

namespace openvpn::PKType {

enum Type
{
    PK_UNKNOWN = 0,
    PK_NONE,
    PK_DSA,
    PK_RSA,
    PK_EC,
    PK_ECDSA,
};

} // namespace openvpn::PKType
