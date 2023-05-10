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

#ifndef OPENVPN_COMMON_EXTERN_H
#define OPENVPN_COMMON_EXTERN_H

#ifndef OPENVPN_EXTERN
// Remember that OPENVPN_EXTERN was not defined since something like
// #if OPENVPN_EXTERN == extern or OPENVPN_EXTERN == "" is not allowed
// in C/C++ preprocessor
#define OPENVPN_NO_EXTERN
#define OPENVPN_EXTERN
#endif

#endif
