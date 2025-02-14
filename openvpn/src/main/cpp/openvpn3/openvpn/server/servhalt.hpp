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

namespace openvpn::HaltRestart {
enum Type
{
    HALT,            // disconnect
    RESTART,         // restart, don't preserve session token
    RESTART_PSID,    // restart, preserve session token
    RESTART_PASSIVE, // restart, preserve session token and local client instance object
    AUTH_FAILED,     // auth fail, don't preserve session token
    RAW,             // pass raw message to client
};

inline std::string to_string(Type type)
{
    switch (type)
    {
    case HALT:
        return "HALT";
    case RESTART:
        return "RESTART";
    case RESTART_PSID:
        return "RESTART_PSID";
    case RESTART_PASSIVE:
        return "RESTART_PASSIVE";
    case AUTH_FAILED:
        return "AUTH_FAILED";
    case RAW:
        return "RAW";
    default:
        return "HaltRestart_?";
    }
}
} // namespace openvpn::HaltRestart
