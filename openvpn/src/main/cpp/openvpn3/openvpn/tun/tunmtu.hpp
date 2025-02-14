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

#ifndef OPENVPN_TUN_TUNMTU_H
#define OPENVPN_TUN_TUNMTU_H

#include <openvpn/common/options.hpp>

namespace openvpn {
enum
{
    TUN_MTU_DEFAULT = 1500,
};

inline unsigned int parse_tun_mtu(const OptionList &opt, unsigned int default_value)
{
    return opt.get_num<unsigned int>("tun-mtu", 1, default_value, 576, 65535);
}

inline unsigned int parse_tun_mtu_max(const OptionList &opt, unsigned int default_value)
{
    return opt.get_num<unsigned int>("tun-mtu-max", 1, default_value, 576, 65535);
}
} // namespace openvpn

#endif
