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

// Base classes for Emulate Excluded Routes

#ifndef OPENVPN_TUN_CLIENT_EMUEXR_H
#define OPENVPN_TUN_CLIENT_EMUEXR_H

#include <openvpn/common/rc.hpp>
#include <openvpn/client/ipverflags.hpp>
#include <openvpn/tun/builder/base.hpp>

namespace openvpn {
struct EmulateExcludeRoute : public RC<thread_unsafe_refcount>
{
    typedef RCPtr<EmulateExcludeRoute> Ptr;

    virtual void add_route(const bool add, const IP::Addr &addr, const int prefix_len) = 0;
    virtual bool enabled(const IPVerFlags &ipv) const = 0;
    virtual void emulate(TunBuilderBase *tb, IPVerFlags &ipv, const IP::Addr &server_addr) const = 0;
    virtual void add_default_routes(bool ipv4, bool ipv6) = 0;
};

struct EmulateExcludeRouteFactory : public RC<thread_unsafe_refcount>
{
    typedef RCPtr<EmulateExcludeRouteFactory> Ptr;

    virtual EmulateExcludeRoute::Ptr new_obj() const = 0;
};
} // namespace openvpn

#endif
