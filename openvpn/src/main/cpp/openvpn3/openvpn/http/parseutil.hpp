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
//
//  Adapted from code Copyright (c) 2003-2012 Christopher M. Kohlhoff (chris at kohlhoff dot com)
//
//  Distributed under the Boost Software License, Version 1.0. (See accompanying
//  file LICENSE_1_0.txt or copy at http://www.boost.org/LICENSE_1_0.txt)

// Common utility methods for HTTP classes

#ifndef OPENVPN_HTTP_PARSEUTIL_H
#define OPENVPN_HTTP_PARSEUTIL_H

namespace openvpn {
  namespace HTTP {
    namespace Util {

      // Check if a byte is an HTTP character.
      inline bool is_char(const unsigned char c)
      {
	return c <= 127;
      }

      // Check if a byte is an HTTP control character.
      inline bool is_ctl(const unsigned char c)
      {
	return (c <= 31)|| (c == 127);
      }

      // Check if a byte is defined as an HTTP tspecial character.
      inline bool is_tspecial(const unsigned char c)
      {
	switch (c)
	  {
	  case '(': case ')': case '<': case '>': case '@':
	  case ',': case ';': case ':': case '\\': case '"':
	  case '/': case '[': case ']': case '?': case '=':
	  case '{': case '}': case ' ': case '\t':
	    return true;
	  default:
	    return false;
	  }
      }

      // Check if a byte is a digit.
      inline bool is_digit(const unsigned char c)
      {
	return c >= '0' && c <= '9';
      }

      // Check if char should be URL-escaped
      inline bool is_escaped(const unsigned char c)
      {
	if (c >= 'a' && c <= 'z')
	  return false;
	if (c >= 'A' && c <= 'Z')
	  return false;
	if (c >= '0' && c <= '9')
	  return false;
	if (c == '.' || c == '-' || c == '_')
	  return false;
	return true;
      }
    }
  }
}

#endif
