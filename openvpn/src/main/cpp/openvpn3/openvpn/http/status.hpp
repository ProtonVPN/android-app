//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012- OpenVPN Inc.
//
//    SPDX-License-Identifier: MPL-2.0 OR AGPL-3.0-only WITH openvpn3-openssl-exception
//

#ifndef OPENVPN_HTTP_STATUS_H
#define OPENVPN_HTTP_STATUS_H

// HTTP status codes

namespace openvpn::HTTP::Status {
enum
{
    OK = 200,
    Connected = 200,
    SwitchingProtocols = 101,
    BadRequest = 400,
    Unauthorized = 401,
    Forbidden = 403,
    NotFound = 404,
    ProxyAuthenticationRequired = 407,
    PreconditionFailed = 412,
    InternalServerError = 500,
    ProxyError = 502,
    ServiceUnavailable = 503,
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
} // namespace openvpn::HTTP::Status

#endif
