//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2017 OpenVPN Inc.
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

#ifndef OPENVPN_ADDR_IPERR_H
#define OPENVPN_ADDR_IPERR_H

#include <string>

#include <openvpn/io/io.hpp>

namespace openvpn {
  namespace IP {
    namespace internal {
      // Called internally by IP, IPv4, and IPv6 classes

      inline std::string format_error(const std::string& ipstr, const char *title, const char *ipver, const openvpn_io::error_code& ec)
      {
	std::string err = "error parsing";
	if (title)
	  {
	    err += ' ';
	    err += title;
	  }
	err += " IP";
	err += ipver;
	err += " address '";
	err += ipstr;
	err += "' : ";
	err += ec.message();
	return err;
      }

      inline std::string format_error(const std::string& ipstr, const char *title, const char *ipver, const char *message)
      {
	std::string err = "error parsing";
	if (title)
	  {
	    err += ' ';
	    err += title;
	  }
	err += " IP";
	err += ipver;
	err += " address '";
	err += ipstr;
	err += '\'';
	if (message)
	  {
	    err += " : ";
	    err += message;
	  }
	return err;
      }
    }
  }
}

#endif
