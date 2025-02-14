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

#ifndef OPENVPN_TUN_BUILDER_RGWFLAGS_H
#define OPENVPN_TUN_BUILDER_RGWFLAGS_H

namespace openvpn::RGWFlags {
// These flags are passed as the flags argument to TunBuilderBase::tun_builder_reroute_gw
// NOTE: must not collide with RG_x flags in rgopt.hpp.
enum
{
    EmulateExcludeRoutes = (1 << 16),
};
} // namespace openvpn::RGWFlags

#endif
