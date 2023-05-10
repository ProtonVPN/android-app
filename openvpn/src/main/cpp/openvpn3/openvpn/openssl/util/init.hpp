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

#include <openssl/opensslv.h>

// OpenSSL 1.1.0 does not require an explicit init, in fact the
// asio init for 1.1.0 is a noop, see also OPENSSL_init_ssl man page

#if (OPENSSL_VERSION_NUMBER < 0x10100000L)
#define OPENSSL_NEEDS_INIT

// Instantiate this object to ensure openssl is initialised.
#ifdef USE_ASIO
#include <asio/ssl/detail/openssl_init.hpp>
typedef asio::ssl::detail::openssl_init<> openssl_init;
#else
#error no OpenSSL init code (USE_ASIO needed for OpenSSL < 1.1)
#endif
#endif