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

// define very basic types such as size_t, ssize_t

#ifndef OPENVPN_COMMON_SIZE_H
#define OPENVPN_COMMON_SIZE_H

#include <cstddef> // defines std::size_t

#include <openvpn/common/platform.hpp>

#ifdef OPENVPN_PLATFORM_WIN
#if !defined(_SSIZE_T_) && !defined(_SSIZE_T_DEFINED) && !defined(HAVE_SSIZE_T)
#include <BaseTsd.h>
typedef SSIZE_T ssize_t;
#define _SSIZE_T_
#define _SSIZE_T_DEFINED
#endif
#else
#include <unistd.h> // get ssize_t
#endif

#endif
