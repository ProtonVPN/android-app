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

#pragma once

#include <openvpn/common/exception.hpp>

namespace openvpn::HTTP {
inline bool is_valid_uri_char(const unsigned char c)
{
    return c >= 0x21 && c <= 0x7E;
}

inline bool is_valid_uri_char(const char c)
{
    return is_valid_uri_char((unsigned char)c);
}

inline void validate_uri(const std::string &uri, const std::string &title)
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

} // namespace openvpn::HTTP
