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

#include <openvpn/common/rc.hpp>
#include <openvpn/acceptor/base.hpp>

namespace openvpn {
// generic structure implemented by the various proxies used by PGProxy
struct ProxyListener : public Acceptor::ListenerBase
{
    typedef RCPtr<ProxyListener> Ptr;

    virtual void start() = 0;
    virtual void stop() = 0;
};
} // namespace openvpn
