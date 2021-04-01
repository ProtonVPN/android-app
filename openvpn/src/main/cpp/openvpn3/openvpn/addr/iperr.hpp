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

// Called internally by IP, IPv4, and IPv6 classes

#pragma once

#include <string>

#include <openvpn/io/io.hpp>

#include <openvpn/common/stringtempl2.hpp>

namespace openvpn {
  namespace IP {
    namespace internal {

      template <typename TITLE>
      inline std::string format_error(const std::string& ipstr,
				      const TITLE& title,
				      const char *ipver,
				      const std::string& message)
      {
	std::string err = "error parsing";
	if (!StringTempl::empty(title))
	  {
	    err += ' ';
	    err += StringTempl::to_string(title);
	  }
	err += " IP";
	err += ipver;
	err += " address '";
	err += ipstr;
	err += '\'';
	if (!message.empty())
	  {
	    err += " : ";
	    err += message;
	  }
	return err;
      }

      template <typename TITLE>
      inline std::string format_error(const std::string& ipstr,
				      const TITLE& title,
				      const char *ipver,
				      const openvpn_io::error_code& ec)
      {
	return format_error(ipstr, title, ipver, ec.message());
      }

    }
  }
}
