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

// Method to set up a particular OpenSSL engine type

#ifndef OPENVPN_OPENSSL_UTIL_ENGINE_H
#define OPENVPN_OPENSSL_UTIL_ENGINE_H

#include <string>

#if (OPENSSL_VERSION_NUMBER >= 0x30000000L)
#define OPENSSL_NO_ENGINE
#endif

#ifndef OPENSSL_NO_ENGINE
#include <openssl/engine.h>
#endif

#include <openvpn/common/exception.hpp>
#include <openvpn/openssl/util/error.hpp>

namespace openvpn {

  OPENVPN_EXCEPTION(openssl_engine_error);

  inline void openssl_setup_engine (const std::string& engine)
  {
#ifndef OPENSSL_NO_ENGINE
    ENGINE_load_builtin_engines ();

    if (engine == "auto")
      {
	ENGINE_register_all_complete ();
	return;
      }

    ENGINE *e = ENGINE_by_id (engine.c_str());
    if (!e)
      throw openssl_engine_error();
    if (!ENGINE_set_default (e, ENGINE_METHOD_ALL))
      throw openssl_engine_error();
#endif
  }

} // namespace openvpn

#endif // OPENVPN_OPENSSL_UTIL_ENGINE_H
