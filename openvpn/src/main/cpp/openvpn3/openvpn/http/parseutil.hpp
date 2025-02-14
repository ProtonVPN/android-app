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
//
// Source: pkg:github/chriskohlhoff/asio@asio-1-8-0#asio/src/examples/http
//  Adapted from code Copyright (c) 2003-2012 Christopher M. Kohlhoff (chris at kohlhoff dot com)
//
//  Distributed under the Boost Software License, Version 1.0. (See accompanying
//  file LICENSE_1_0.txt or copy at http://www.boost.org/LICENSE_1_0.txt)

// Common utility methods for HTTP classes

#ifndef OPENVPN_HTTP_PARSEUTIL_H
#define OPENVPN_HTTP_PARSEUTIL_H

namespace openvpn::HTTP::Util {

// Check if a byte is an HTTP character.
inline bool is_char(const unsigned char c)
{
    return c <= 127;
}

// Check if a byte is an HTTP control character.
inline bool is_ctl(const unsigned char c)
{
    return (c <= 31) || (c == 127);
}

// Check if a byte is defined as an HTTP tspecial character.
inline bool is_tspecial(const unsigned char c)
{
    switch (c)
    {
    case '(':
    case ')':
    case '<':
    case '>':
    case '@':
    case ',':
    case ';':
    case ':':
    case '\\':
    case '"':
    case '/':
    case '[':
    case ']':
    case '?':
    case '=':
    case '{':
    case '}':
    case ' ':
    case '\t':
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
} // namespace openvpn::HTTP::Util

#endif
