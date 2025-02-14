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

#ifndef OPENVPN_TRANSPORT_CLIENT_EXTERN_FW_H
#define OPENVPN_TRANSPORT_CLIENT_EXTERN_FW_H

#ifdef OPENVPN_EXTERNAL_TRANSPORT_FACTORY
#include <openvpn/transport/client/transbase.hpp>
#endif

namespace openvpn::ExternalTransport {
#ifdef OPENVPN_EXTERNAL_TRANSPORT_FACTORY
struct Config;
struct Factory
{
    virtual TransportClientFactory *new_transport_factory(const Config &conf) = 0;
    virtual ~Factory() = default;
};
#else
struct Factory
{
};
#endif
} // namespace openvpn::ExternalTransport
#endif
