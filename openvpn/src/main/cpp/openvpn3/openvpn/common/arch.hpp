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

// define an ARCH_x macro that describes our target architecture

#ifndef OPENVPN_COMMON_ARCH_H
#define OPENVPN_COMMON_ARCH_H

#if defined(__amd64__) || defined(__x86_64__) || defined(_M_X64) || defined(_M_AMD64)
# define OPENVPN_ARCH_x86_64
#elif defined(__i386__) || defined(_M_IX86)
# define OPENVPN_ARCH_i386
#elif defined(__aarch64__) || defined(__arm64__)
# define OPENVPN_ARCH_ARM64
#elif defined(__arm__) || defined(_M_ARM)
# define OPENVPN_ARCH_ARM
#endif

#endif
