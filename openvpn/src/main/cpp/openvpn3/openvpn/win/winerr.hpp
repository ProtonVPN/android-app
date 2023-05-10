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

// Windows error utilities

#ifndef OPENVPN_WIN_WINERR_H
#define OPENVPN_WIN_WINERR_H

#include <windows.h>

#include <openvpn/io/io.hpp>

namespace openvpn {
namespace Win {
struct Error : public openvpn_io::error_code
{
    Error(const DWORD err)
        : openvpn_io::error_code(err, openvpn_io::error::get_system_category())
    {
    }
};

struct LastError : public Error
{
    LastError()
        : Error(::GetLastError())
    {
    }
};
} // namespace Win
} // namespace openvpn

#endif
