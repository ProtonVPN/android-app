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

#ifndef OPENVPN_COMMON_LIKELY_H
#define OPENVPN_COMMON_LIKELY_H

// Branch prediction hints (these make a difference on ARM)
#if !defined(likely) && !defined(unlikely)
#if defined(__GNUC__)
# define likely(x)    __builtin_expect((x),1)
# define unlikely(x)  __builtin_expect((x),0)
#else
# define likely(x)    (x)
# define unlikely(x)  (x)
#endif
#endif

#endif // OPENVPN_COMMON_LIKELY_H
