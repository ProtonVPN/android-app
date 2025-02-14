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

// Simple logging with data/time prepend

#pragma once

#include <iostream>
#include <openvpn/time/timestr.hpp>

#ifndef OPENVPN_LOG_STREAM
#define OPENVPN_LOG_STREAM std::cout
#endif

#define OPENVPN_LOG(args) OPENVPN_LOG_STREAM << date_time() << ' ' << args << std::endl

// like OPENVPN_LOG but no trailing newline
#define OPENVPN_LOG_NTNL(args) OPENVPN_LOG_STREAM << date_time() << ' ' << args

#define OPENVPN_LOG_STRING(str) OPENVPN_LOG_STREAM << date_time() << ' ' << (str)

// no-op constructs normally used with logthread.hpp
namespace openvpn::Log {
struct Context
{
    struct Wrapper
    {
    };
    Context(const Wrapper &)
    {
    }
};
} // namespace openvpn::Log
