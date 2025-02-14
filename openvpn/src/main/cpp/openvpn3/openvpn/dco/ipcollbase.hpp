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
#include <sstream>
#include <unordered_map>
#include <mutex>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/action.hpp>
#include <openvpn/addr/ip.hpp>

namespace openvpn {
class IPCollisionDetectBase
{
  public:
    OPENVPN_EXCEPTION(ip_collision);

    virtual void add(const std::string &addr_str,
                     const unsigned int unit,
                     ActionList &late_remove)
    {
    }
};

} // namespace openvpn
