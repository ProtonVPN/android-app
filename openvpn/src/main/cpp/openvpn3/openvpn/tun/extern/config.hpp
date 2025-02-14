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

#ifndef OPENVPN_TUN_EXTERN_CONFIG_H
#define OPENVPN_TUN_EXTERN_CONFIG_H

// These includes are also intended to resolve forward references in fw.hpp
#include <openvpn/common/options.hpp>
#include <openvpn/tun/client/tunbase.hpp>
#include <openvpn/tun/client/tunprop.hpp>
#include <openvpn/frame/frame.hpp>
#include <openvpn/log/sessionstats.hpp>
#include <openvpn/common/stop.hpp>

namespace openvpn::ExternalTun {
struct Config
{
    TunProp::Config tun_prop;
    Frame::Ptr frame;
    SessionStats::Ptr stats;
    Stop *stop = nullptr;
    bool tun_persist = false;
};
} // namespace openvpn::ExternalTun
#endif
