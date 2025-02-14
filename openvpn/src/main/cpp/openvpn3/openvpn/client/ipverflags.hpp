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

#ifndef OPENVPN_CLIENT_IPVERFLAGS_H
#define OPENVPN_CLIENT_IPVERFLAGS_H

#include <openvpn/addr/ip.hpp>
#include <openvpn/client/rgopt.hpp>
#include <openvpn/tun/builder/rgwflags.hpp>

namespace openvpn {
class IPVerFlags
{
  public:
    IPVerFlags(const OptionList &opt,
               const IP::Addr::VersionMask ip_ver_flags)
        : ip_ver_flags_(ip_ver_flags),
          rg_flags_(opt),
          api_flags_(0)
    {
    }

    bool rgv4() const
    {
        return v4() && rg_flags_.redirect_gateway_ipv4_enabled();
    }

    bool rgv6() const
    {
        return v6() && rg_flags_.redirect_gateway_ipv6_enabled();
    }

    bool v4() const
    {
        return (ip_ver_flags_ & IP::Addr::V4_MASK) ? true : false;
    }

    bool v6() const
    {
        return (ip_ver_flags_ & IP::Addr::V6_MASK) ? true : false;
    }

    IP::Addr::VersionMask rg_ver_flags() const
    {
        IP::Addr::VersionMask flags = 0;
        if (rgv4())
            flags |= IP::Addr::V4_MASK;
        if (rgv6())
            flags |= IP::Addr::V6_MASK;
        return flags;
    }

    IP::Addr::VersionMask ip_ver_flags() const
    {
        IP::Addr::VersionMask flags = 0;
        if (v4())
            flags |= IP::Addr::V4_MASK;
        if (v6())
            flags |= IP::Addr::V6_MASK;
        return flags;
    }

    // these flags are passed to tun_builder_reroute_gw method
    unsigned int api_flags() const
    {
        return api_flags_ | rg_flags_();
    }

    void set_emulate_exclude_routes()
    {
        api_flags_ |= RGWFlags::EmulateExcludeRoutes;
    }

  private:
    const IP::Addr::VersionMask ip_ver_flags_;
    const RedirectGatewayFlags rg_flags_;
    unsigned int api_flags_;
};
} // namespace openvpn

#endif
