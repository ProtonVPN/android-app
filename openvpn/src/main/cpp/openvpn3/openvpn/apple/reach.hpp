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

#ifndef OPENVPN_APPLECRYPTO_UTIL_REACH_H
#define OPENVPN_APPLECRYPTO_UTIL_REACH_H

// An interface to various network reachability implementations,
// primarily for iOS.

namespace openvpn {
struct ReachabilityInterface
{
    enum Status
    {
        NotReachable,
        ReachableViaWiFi,
        ReachableViaWWAN
    };

    virtual Status reachable() const = 0;
    virtual bool reachableVia(const std::string &net_type) const = 0;
    virtual std::string to_string() const = 0;
    virtual ~ReachabilityInterface() = default;
};
} // namespace openvpn
#endif
