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

#ifndef OPENVPN_COMMON_HOSTPORT_H
#define OPENVPN_COMMON_HOSTPORT_H

#include <string>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/number.hpp>
#include <openvpn/common/options.hpp>
#include <openvpn/common/unicode.hpp>

namespace openvpn {
  namespace HostPort {
    OPENVPN_EXCEPTION(host_port_error);

    inline bool is_valid_port(const unsigned int port)
    {
      return port < 65536;
    }

    inline bool is_valid_port(const std::string& port, unsigned int *value = nullptr)
    {
      return parse_number_validate<unsigned int>(port, 5, 1, 65535, value);
    }

    inline void validate_port(const std::string& port, const std::string& title, unsigned int *value = nullptr)
    {
      if (!is_valid_port(port, value))
	OPENVPN_THROW(host_port_error, "bad " << title << " port number: " << Unicode::utf8_printable(port, 16));
    }

    inline void validate_port(const unsigned int port, const std::string& title)
    {
      if (!is_valid_port(port))
	OPENVPN_THROW(host_port_error, "bad " << title << " port number: " << port);
    }

    inline unsigned short parse_port(const std::string& port, const std::string& title)
    {
      unsigned int ret = 0;
      validate_port(port, title, &ret);
      return ret;
    }

    // An IP address is also considered to be a valid host
    inline bool is_valid_host_char(const char c)
    {
      return (c >= 'a' && c <= 'z')
	|| (c >= 'A' && c <= 'Z')
	|| (c >= '0' && c <= '9')
	|| c == '.'
	|| c == '-'
	|| c == ':'; // for IPv6
    }

    inline bool is_valid_host(const std::string& host)
    {
      if (!host.length() || host.length() > 256)
	return false;
      for (const auto &c : host)
	{
	  if (!is_valid_host_char(c))
	    return false;
	}
      return true;
    }

    inline bool is_valid_unix_sock_char(const unsigned char c)
    {
      return c >= 0x21 && c <= 0x7E;
    }

    inline bool is_valid_unix_sock(const std::string& host)
    {
      if (!host.length() || host.length() > 256)
	return false;
      for (const auto &c : host)
	{
	  if (!is_valid_unix_sock_char(c))
	    return false;
	}
      return true;
    }

    inline void validate_host(const std::string& host, const std::string& title)
    {
      if (!is_valid_host(host))
	OPENVPN_THROW(host_port_error, "bad " << title << " host: " << Unicode::utf8_printable(host, 64));
    }

    inline bool split_host_port(const std::string& str,
				std::string& host,
				std::string& port,
				const std::string& default_port,
				const bool allow_unix,
				unsigned int *port_save = nullptr)
    {
      if (port_save)
	*port_save = 0;
      const size_t pos = str.find_last_of(':');
      const size_t cb = str.find_last_of(']');
      if (pos != std::string::npos && (cb == std::string::npos || pos > cb))
	{
	  // host:port or [host]:port specified
	  host = str.substr(0, pos);
	  port = str.substr(pos + 1);
	}
      else if (!default_port.empty())
	{
	  // only host specified
	  host = str;
	  port = default_port;
	}
      else
	return false;

      // unbracket host
      if (host.length() >= 2 && host[0] == '[' && host[host.length()-1] == ']')
	host = host.substr(1, host.length()-2);

      if (allow_unix && port == "unix")
	return is_valid_unix_sock(host);
      else
	return is_valid_host(host) && is_valid_port(port, port_save);
    }

  }
}

#endif
