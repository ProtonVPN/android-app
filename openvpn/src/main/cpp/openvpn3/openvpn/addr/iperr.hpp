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

// Called internally by IP, IPv4, and IPv6 classes

#pragma once

#include <string>

#include <openvpn/io/io.hpp>

#ifndef OPENVPN_LEGACY_TITLE_ABSTRACTION
#include <openvpn/common/stringtempl2.hpp>
#endif

namespace openvpn::IP::internal {

#ifndef OPENVPN_LEGACY_TITLE_ABSTRACTION

template <typename TITLE>
inline std::string format_error(const std::string &ipstr,
                                const TITLE &title,
                                const char *ipver,
                                const std::string &message)
{
    std::string err = "error parsing";
    if (!StringTempl::empty(title))
    {
        err += ' ';
        err += StringTempl::to_string(title);
    }
    err += " IP";
    err += ipver;
    err += " address '";
    err += ipstr;
    err += '\'';
    if (!message.empty())
    {
        err += " : ";
        err += message;
    }
    return err;
}

template <typename TITLE>
inline std::string format_error(const std::string &ipstr,
                                const TITLE &title,
                                const char *ipver,
                                const openvpn_io::error_code &ec)
{
    return format_error(ipstr, title, ipver, ec.message());
}

#else

inline std::string format_error(const std::string &ipstr, const char *title, const char *ipver, const openvpn_io::error_code &ec)
{
    std::string err = "error parsing";
    if (title)
    {
        err += ' ';
        err += title;
    }
    err += " IP";
    err += ipver;
    err += " address '";
    err += ipstr;
    err += "' : ";
    err += ec.message();
    return err;
}

inline std::string format_error(const std::string &ipstr, const char *title, const char *ipver, const char *message)
{
    std::string err = "error parsing";
    if (title)
    {
        err += ' ';
        err += title;
    }
    err += " IP";
    err += ipver;
    err += " address '";
    err += ipstr;
    err += '\'';
    if (message)
    {
        err += " : ";
        err += message;
    }
    return err;
}

#endif
} // namespace openvpn::IP::internal
