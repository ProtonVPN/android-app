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

#include <openvpn/ssl/sslapi.hpp>
#include <openvpn/ssl/sni_metadata.hpp>

namespace openvpn::SNI {

// Abstract base class used to provide an SNI handler
class HandlerBase
{
  public:
    typedef std::unique_ptr<HandlerBase> UPtr;

    // Return a new SSLFactoryAPI for this SNI name.
    // Implementation may also set sni_metadata.
    // Return SSLFactoryAPI::Ptr() if sni_name is not recognized.
    // The caller guarantees that sni_name is valid UTF-8 and
    // doesn't contain any control characters.

    // clang-format off
    virtual SSLFactoryAPI::Ptr sni_hello(const std::string &sni_name,
                                         SNI::Metadata::UPtr &sni_metadata,
                                         SSLConfigAPI::Ptr default_factory) const = 0;
    // clang-format on

    virtual ~HandlerBase() = default;
};

} // namespace openvpn::SNI
