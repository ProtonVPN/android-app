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

#ifndef OPENVPN_TUN_BUILDER_RGWFLAGS_H
#define OPENVPN_TUN_BUILDER_RGWFLAGS_H

namespace openvpn {
namespace RGWFlags {
// These flags are passed as the flags argument to TunBuilderBase::tun_builder_reroute_gw
// NOTE: must not collide with RG_x flags in rgopt.hpp.
enum
{
    EmulateExcludeRoutes = (1 << 16),
};
} // namespace RGWFlags
} // namespace openvpn

#endif
