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

#ifndef OPENVPN_CLIENT_CLILIFE_H
#define OPENVPN_CLIENT_CLILIFE_H

#include <string>

#include <openvpn/common/rc.hpp>

namespace openvpn {
// Base class for managing connection lifecycle notifications,
// such as sleep, wakeup, network-unavailable, network-available.
class ClientLifeCycle : public RC<thread_unsafe_refcount>
{
  public:
    struct NotifyCallback
    {
        virtual ~NotifyCallback() = default;

        virtual void cln_stop() = 0;
        virtual void cln_pause(const std::string &reason) = 0;
        virtual void cln_resume() = 0;
        virtual void cln_reconnect(int seconds) = 0;
    };

    typedef RCPtr<ClientLifeCycle> Ptr;

    virtual bool network_available() = 0;

    virtual void start(NotifyCallback *) = 0;
    virtual void stop() = 0;
};
} // namespace openvpn

#endif
