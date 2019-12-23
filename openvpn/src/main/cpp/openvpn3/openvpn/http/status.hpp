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

#ifndef OPENVPN_HTTP_STATUS_H
#define OPENVPN_HTTP_STATUS_H

// HTTP status codes

namespace openvpn {
  namespace HTTP {
    namespace Status {
      enum {
	OK=200,
	Connected=200,
	SwitchingProtocols=101,
	BadRequest=400,
	Unauthorized=401,
	Forbidden=403,
	NotFound=404,
	ProxyAuthenticationRequired=407,
	PreconditionFailed=412,
	InternalServerError=500,
	ProxyError=502,
	ServiceUnavailable=503,
      };

      inline const char *to_string(const int status)
      {
	switch (status)
	  {
	  case OK:
	    return "OK";
	  case SwitchingProtocols:
	    return "Switching Protocols";
	  case BadRequest:
	    return "Bad Request";
	  case Unauthorized:
	    return "Unauthorized";
	  case Forbidden:
	    return "Forbidden";
	  case NotFound:
	    return "Not Found";
	  case ProxyAuthenticationRequired:
	    return "Proxy Authentication Required";
	  case PreconditionFailed:
	    return "Precondition Failed";
	  case InternalServerError:
	    return "Internal Server Error";
	  case ProxyError:
	    return "Proxy Error";
	  case ServiceUnavailable:
	    return "Service Unavailable";
	  default:
	    return "";
	  }
      }
    }
  }
}

#endif
