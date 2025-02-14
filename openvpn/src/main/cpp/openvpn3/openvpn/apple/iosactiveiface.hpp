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

#include <string>

#include <openvpn/apple/reach.hpp>
#include <openvpn/netconf/enumiface.hpp>

#ifndef OPENVPN_APPLECRYPTO_UTIL_IOSACTIVEIFACE_H
#define OPENVPN_APPLECRYPTO_UTIL_IOSACTIVEIFACE_H

namespace openvpn {

class iOSActiveInterface : public ReachabilityInterface
{
  public:
    Status reachable() const override
    {
        if (ei.iface_up("en0"))
            return ReachableViaWiFi;
        else if (ei.iface_up("pdp_ip0"))
            return ReachableViaWWAN;
        else
            return NotReachable;
    }

    bool reachableVia(const std::string &net_type) const override
    {
        const Status r = reachable();
        if (net_type == "cellular")
            return r == ReachableViaWWAN;
        else if (net_type == "wifi")
            return r == ReachableViaWiFi;
        else
            return r != NotReachable;
    }

    std::string to_string() const override
    {
        switch (reachable())
        {
        case ReachableViaWiFi:
            return "ReachableViaWiFi";
        case ReachableViaWWAN:
            return "ReachableViaWWAN";
        case NotReachable:
            return "NotReachable";
        }
    }

  private:
    EnumIface ei;
};

} // namespace openvpn
#endif
