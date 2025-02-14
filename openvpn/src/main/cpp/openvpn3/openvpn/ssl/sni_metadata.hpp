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

#include <string>
#include <memory>

namespace openvpn {

class AuthCert;

namespace SNI {

class Metadata
{
  public:
    typedef std::unique_ptr<Metadata> UPtr;

    virtual std::string sni_client_name(const AuthCert &ac) const = 0;

    virtual ~Metadata() = default;
};

} // namespace SNI
} // namespace openvpn
