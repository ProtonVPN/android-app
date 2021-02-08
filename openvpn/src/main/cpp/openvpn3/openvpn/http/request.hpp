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
//
//  Adapted from code Copyright (c) 2003-2012 Christopher M. Kohlhoff (chris at kohlhoff dot com)
//
//  Distributed under the Boost Software License, Version 1.0. (See accompanying
//  file LICENSE_1_0.txt or copy at http://www.boost.org/LICENSE_1_0.txt)

// Parse an HTTP request

#ifndef OPENVPN_HTTP_REQUEST_H
#define OPENVPN_HTTP_REQUEST_H

#include <openvpn/http/header.hpp>
#include <openvpn/http/parseutil.hpp>

namespace openvpn {
  namespace HTTP {

    struct Request {
      Request() : http_version_major(0), http_version_minor(0) {}

      void reset()
      {
	method = "";
	uri = "";
	http_version_major = 0;
	http_version_minor = 0;
	headers.clear();
      }

      std::string to_string() const
      {
	std::ostringstream out;
	out << "HTTP Request" << std::endl;
	out << "method=" << method << std::endl;
	out << "uri=" << uri << std::endl;
	out << "version=" << http_version_major << '/' << http_version_minor << std::endl;
	out << headers.to_string();
	return out.str();
      }

      std::string to_string_compact() const
      {
	std::ostringstream out;
	out << method << ' ' << uri << " HTTP/" << http_version_major << '.' << http_version_minor;
	return out.str();
      }

      bool at_least_http_1_1() const
      {
	return http_version_major > 1 || (http_version_major == 1 && http_version_minor >= 1);
      }

      std::string method;
      std::string uri;
      int http_version_major;
      int http_version_minor;
      HeaderList headers;
    };

    class RequestParser {
      enum state
	{
	  method_start,
	  method,
	  uri,
	  http_version_h,
	  http_version_t_1,
	  http_version_t_2,
	  http_version_p,
	  http_version_slash,
	  http_version_major_start,
	  http_version_major,
	  http_version_minor_start,
	  http_version_minor,
	  expecting_newline_1,
	  header_line_start,
	  header_lws,
	  header_name,
	  space_before_header_value,
	  header_value,
	  expecting_newline_2,
	  expecting_newline_3
	};

    public:
      enum status {
	pending,
	fail,
	success,
      };

      RequestParser()
	: state_(method_start)
      {
      }

      // Reset to initial parser state.
      void reset()
      {
	state_ = method_start;
      }

      // Parse some HTTP request data.
      status consume(Request& req, const unsigned char input)
      {
	switch (state_)
	  {
	  case method_start:
	    if (!Util::is_char(input) || Util::is_ctl(input) || Util::is_tspecial(input))
	      {
		return fail;
	      }
	    else
	      {
		state_ = method;
		req.method.push_back(input);
		return pending;
	      }
	  case method:
	    if (input == ' ')
	      {
		state_ = uri;
		return pending;
	      }
	    else if (!Util::is_char(input) || Util::is_ctl(input) || Util::is_tspecial(input))
	      {
		return fail;
	      }
	    else
	      {
		req.method.push_back(input);
		return pending;
	      }
	  case uri:
	    if (input == ' ')
	      {
		state_ = http_version_h;
		return pending;
	      }
	    else if (Util::is_ctl(input))
	      {
		return fail;
	      }
	    else
	      {
		req.uri.push_back(input);
		return pending;
	      }
	  case http_version_h:
	    if (input == 'H')
	      {
		state_ = http_version_t_1;
		return pending;
	      }
	    else
	      {
		return fail;
	      }
	  case http_version_t_1:
	    if (input == 'T')
	      {
		state_ = http_version_t_2;
		return pending;
	      }
	    else
	      {
		return fail;
	      }
	  case http_version_t_2:
	    if (input == 'T')
	      {
		state_ = http_version_p;
		return pending;
	      }
	    else
	      {
		return fail;
	      }
	  case http_version_p:
	    if (input == 'P')
	      {
		state_ = http_version_slash;
		return pending;
	      }
	    else
	      {
		return fail;
	      }
	  case http_version_slash:
	    if (input == '/')
	      {
		req.http_version_major = 0;
		req.http_version_minor = 0;
		state_ = http_version_major_start;
		return pending;
	      }
	    else
	      {
		return fail;
	      }
	  case http_version_major_start:
	    if (Util::is_digit(input))
	      {
		req.http_version_major = req.http_version_major * 10 + input - '0';
		state_ = http_version_major;
		return pending;
	      }
	    else
	      {
		return fail;
	      }
	  case http_version_major:
	    if (input == '.')
	      {
		state_ = http_version_minor_start;
		return pending;
	      }
	    else if (Util::is_digit(input))
	      {
		req.http_version_major = req.http_version_major * 10 + input - '0';
		return pending;
	      }
	    else
	      {
		return fail;
	      }
	  case http_version_minor_start:
	    if (Util::is_digit(input))
	      {
		req.http_version_minor = req.http_version_minor * 10 + input - '0';
		state_ = http_version_minor;
		return pending;
	      }
	    else
	      {
		return fail;
	      }
	  case http_version_minor:
	    if (input == '\r')
	      {
		state_ = expecting_newline_1;
		return pending;
	      }
	    else if (Util::is_digit(input))
	      {
		req.http_version_minor = req.http_version_minor * 10 + input - '0';
		return pending;
	      }
	    else
	      {
		return fail;
	      }
	  case expecting_newline_1:
	    if (input == '\n')
	      {
		state_ = header_line_start;
		return pending;
	      }
	    else
	      {
		return fail;
	      }
	  case header_line_start:
	    if (input == '\r')
	      {
		state_ = expecting_newline_3;
		return pending;
	      }
	    else if (!req.headers.empty() && (input == ' ' || input == '\t'))
	      {
		state_ = header_lws;
		return pending;
	      }
	    else if (!Util::is_char(input) || Util::is_ctl(input) || Util::is_tspecial(input))
	      {
		return fail;
	      }
	    else
	      {
		req.headers.push_back(Header());
		req.headers.back().name.push_back(input);
		state_ = header_name;
		return pending;
	      }
	  case header_lws:
	    if (input == '\r')
	      {
		state_ = expecting_newline_2;
		return pending;
	      }
	    else if (input == ' ' || input == '\t')
	      {
		return pending;
	      }
	    else if (Util::is_ctl(input))
	      {
		return fail;
	      }
	    else
	      {
		state_ = header_value;
		req.headers.back().value.push_back(input);
		return pending;
	      }
	  case header_name:
	    if (input == ':')
	      {
		state_ = space_before_header_value;
		return pending;
	      }
	    else if (!Util::is_char(input) || Util::is_ctl(input) || Util::is_tspecial(input))
	      {
		return fail;
	      }
	    else
	      {
		req.headers.back().name.push_back(input);
		return pending;
	      }
	  case space_before_header_value:
	    if (input == ' ')
	      {
		state_ = header_value;
		return pending;
	      }
	    else
	      {
		return fail;
	      }
	  case header_value:
	    if (input == '\r')
	      {
		state_ = expecting_newline_2;
		return pending;
	      }
	    else if (Util::is_ctl(input))
	      {
		return fail;
	      }
	    else
	      {
		req.headers.back().value.push_back(input);
		return pending;
	      }
	  case expecting_newline_2:
	    if (input == '\n')
	      {
		state_ = header_line_start;
		return pending;
	      }
	    else
	      {
		return fail;
	      }
	  case expecting_newline_3:
	    if (input == '\n')
	      return success;
	    else
	      return fail;
	  default:
	    return fail;
	  }
      }

    private:
      // The current state of the parser.
      state state_;
    };

    struct RequestType
    {
      typedef Request State;
      typedef RequestParser Parser;
    };
  }
}

#endif
