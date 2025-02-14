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

#ifndef OPENVPN_TUN_EXTERN_FW_H
#define OPENVPN_TUN_EXTERN_FW_H

namespace openvpn {

#if defined(OPENVPN_EXTERNAL_TUN_FACTORY)

struct TunClientFactory;
class OptionList;

namespace ExternalTun {
struct Config; // defined in config.hpp
struct Factory
{
    virtual TunClientFactory *new_tun_factory(const Config &conf, const OptionList &opt) = 0;
    virtual ~Factory() = default;
};
} // namespace ExternalTun

#else

namespace ExternalTun {
struct Factory
{
};
} // namespace ExternalTun

#endif
} // namespace openvpn
#endif
