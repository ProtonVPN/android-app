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
#include <signal.h>

namespace openvpn {

inline std::string signal_name(const int signum)
{
    switch (signum)
    {
    case SIGINT:
        return "SIGINT";
    case SIGTERM:
        return "SIGTERM";
    case SIGHUP:
        return "SIGHUP";
    case SIGUSR1:
        return "SIGUSR1";
    case SIGUSR2:
        return "SIGUSR2";
    case SIGPIPE:
        return "SIGPIPE";
    default:
        return std::to_string(signum);
    }
}

} // namespace openvpn
