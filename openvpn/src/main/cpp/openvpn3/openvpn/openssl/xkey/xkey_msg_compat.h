//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2022 OpenVPN Inc.
//    Copyright (C) 2021-2022 Selva Nair <selva.nair@gmail.com>
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

#define msg(flags, ...) openvpn_msg_xkey_compat(flags, __VA_ARGS__);
#define dmsg(flags, ...) openvpn_msg_xkey_compat(flags, __VA_ARGS__);

/* dummy definitions for the flags, not identical with the real values from
 * OpenVPN 2.x */
#define D_XKEY 1u
#define M_NOLF 2u
#define M_WARN 4u
#define M_NOPREFIX 8u
#define M_NONFATAL 16u

void openvpn_msg_xkey_compat(unsigned int flags, const char *format, ...)
#ifdef __GNUC__
__attribute__ ((format(__printf__, 2, 3)))
#endif
;
