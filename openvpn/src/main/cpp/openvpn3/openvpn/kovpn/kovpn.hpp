//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2020 OpenVPN Inc.
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

// OpenVPN 3 wrapper for kovpn

#pragma once

// Not including this file causes redefinition errors
// when the sys/ and linux/ headers below are included
// before Asio.
#include <openvpn/io/io.hpp>

#include <sys/socket.h>

extern "C" {
#include <linux/types.h>
#include <uapi/ovpn/shim.h> // for OVPN_SHIM_MAX
#include <kovpn/kovpn.h>
}
