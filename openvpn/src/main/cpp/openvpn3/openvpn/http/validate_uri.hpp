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

#pragma once

#include <openvpn/common/exception.hpp>

namespace openvpn {
  namespace HTTP {
    inline bool is_valid_uri_char(const unsigned char c)
    {
      return c >= 0x21 && c <= 0x7E;
    }

    inline bool is_valid_uri_char(const char c)
    {
      return is_valid_uri_char((unsigned char)c);
    }

    inline void validate_uri(const std::string& uri, const std::string& title)
    {
      if (uri.empty())
	throw Exception(title + " : URI is empty");
      if (uri[0] != '/')
	throw Exception(title + " : URI must begin with '/'");
      for (auto &c : uri)
	{
	  if (!is_valid_uri_char(c))
	    throw Exception(title + " : URI contains illegal character");
	}
    }

  }
}
