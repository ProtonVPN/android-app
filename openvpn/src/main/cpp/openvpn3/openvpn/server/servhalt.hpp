//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2022 OpenVPN Inc.
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU Affero General Public License Version 3
//    as published by the Free Software Foundation.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU Affero General Public License for more details.
//
//    You should have received a copy of the GNU Affero General Public License
//    along with this program in the COPYING file.
//    If not, see <http://www.gnu.org/licenses/>.

#pragma once

#include <string>

namespace openvpn {
namespace HaltRestart {
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
} // namespace HaltRestart
} // namespace openvpn
