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

// OVPNAGENT_NAME can be passed on build command line.
// Customized agent name is needed with purpose to install
// few app with agents on one OS (e.g OC 3.0 and PT)
#ifdef OVPNAGENT_NAME
#define OVPNAGENT_NAME_STRING OPENVPN_STRINGIZE(OVPNAGENT_NAME)
#else
#define OVPNAGENT_NAME_STRING "ovpnagent"
#endif

#include <openvpn/common/string.hpp>
#include <openvpn/common/path.hpp>

namespace openvpn {
class Agent
{
  public:
    static std::string named_pipe_path()
    {
        return "\\\\.\\pipe\\" OVPNAGENT_NAME_STRING;
    }

    static bool valid_pipe(const std::string &client_exe,
                           const std::string &server_exe)
    {
#ifdef OVPNAGENT_DISABLE_PATH_CHECK
        return true;
#else
        return normalize_exe_path(client_exe) == normalize_exe_path(server_exe);
#endif
    }

  private:
    // If path starts with C:\..., lower-case the drive letter.
    // Then strip off the basename and only return the dir.
    static std::string normalize_exe_path(const std::string &path)
    {
        std::string p;
        if (path.length() >= 3
            && std::isalpha(static_cast<unsigned char>(path[0])) != 0
            && path[1] == ':'
            && path[2] == '\\')
            p = string::to_lower_copy(path.substr(0, 3)) + path.substr(3);
        else
            p = path;
        p = path::dirname(p);
        return p;
    }
};
} // namespace openvpn
