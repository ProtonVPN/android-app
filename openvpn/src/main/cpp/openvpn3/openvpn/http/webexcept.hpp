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

#ifndef OPENVPN_HTTP_EXCEPT_H
#define OPENVPN_HTTP_EXCEPT_H

#include <string>
#include <sstream>
#include <exception>

#include <openvpn/http/status.hpp>

# define OPENVPN_THROW_WEB(exc, status, stuff)	\
  do {						\
    std::ostringstream _ovpn_exc;		\
    _ovpn_exc << stuff;				\
    throw exc(status, _ovpn_exc.str());		\
  } while (0)

namespace openvpn {
  namespace HTTP {
    class WebException : public std::exception
    {
    public:
      WebException(const int status, const std::string& error)
	: status_(status),
	  error_(error),
	  formatted(std::string(Status::to_string(status_)) + " : " + error_)
      {
      }

      WebException(const int status)
	: status_(status),
	  error_(Status::to_string(status_)),
	  formatted(error_)
      {
      }

      int status() const { return status_; }
      const std::string& error() const { return error_; }

      virtual const char* what() const throw() { return formatted.c_str(); }

    private:
      const int status_;
      const std::string error_;
      const std::string formatted;
    };
  }
}

#endif
