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

// Denote the data in an HTTP header

#ifndef OPENVPN_HTTP_HEADER_H
#define OPENVPN_HTTP_HEADER_H

#include <string>
#include <sstream>
#include <utility>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/string.hpp>

namespace openvpn {
  namespace HTTP {

    struct Header {
      Header() {}
      Header(std::string name_arg, std::string value_arg)
	: name(std::move(name_arg)), value(std::move(value_arg)) {}

      bool name_match(const std::string& n) const
      {
	return string::strcasecmp(n, name) == 0;
      }

      std::string to_string() const
      {
	std::ostringstream out;
	out << name << '=' << value;
	return out.str();
      }

      std::string name;
      std::string value;
    };

    struct HeaderList : public std::vector<Header>
    {
      const Header* get(const std::string& key) const
      {
	for (auto &h : *this)
	  {
	    if (h.name_match(key))
	      return &h;
	  }
	return nullptr;
      }

      Header* get(const std::string& key)
      {
	for (auto &h : *this)
	  {
	    if (h.name_match(key))
	      return &h;
	  }
	return nullptr;
      }

      std::string get_value(const std::string& key) const
      {
	const Header* h = get(key);
	if (h)
	  return h->value;
	else
	  return "";
      }

      std::string get_value_trim(const std::string& key) const
      {
	return string::trim_copy(get_value(key));
      }

      std::string get_value_trim_lower(const std::string& key) const
      {
	return string::to_lower_copy(get_value_trim(key));
      }

      std::string to_string() const
      {
	std::ostringstream out;
	for (size_t i = 0; i < size(); ++i)
	  out << '[' << i << "] " << (*this)[i].to_string() << std::endl;
	return out.str();
      }      
    };

  }
}

#endif
