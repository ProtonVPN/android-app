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

// This is a general-purpose logging framework that allows for OPENVPN_LOG and
// OPENVPN_LOG_NTNL macros to dispatch logging data to a thread-local handler.

#pragma once

// Define this parameter before including this header:
// OPENVPN_LOG_INFO  -- converts a log string to the form that should be passed to log()

#ifndef OPENVPN_LOG_INFO
#error OPENVPN_LOG_INFO must be defined
#endif

#define OPENVPN_LOG(args)                                                           \
    do                                                                              \
    {                                                                               \
        if (openvpn::Log::Context::defined())                                       \
        {                                                                           \
            std::ostringstream _ovpn_log;                                           \
            _ovpn_log << args << '\n';                                              \
            (openvpn::Log::Context::obj()->log(OPENVPN_LOG_INFO(_ovpn_log.str()))); \
        }                                                                           \
    } while (0)

// like OPENVPN_LOG but no trailing newline
#define OPENVPN_LOG_NTNL(args)                                                      \
    do                                                                              \
    {                                                                               \
        if (openvpn::Log::Context::defined())                                       \
        {                                                                           \
            std::ostringstream _ovpn_log;                                           \
            _ovpn_log << args;                                                      \
            (openvpn::Log::Context::obj()->log(OPENVPN_LOG_INFO(_ovpn_log.str()))); \
        }                                                                           \
    } while (0)

#define OPENVPN_LOG_STRING(str)                                         \
    do                                                                  \
    {                                                                   \
        if (openvpn::Log::Context::defined())                           \
        {                                                               \
            (openvpn::Log::Context::obj()->log(OPENVPN_LOG_INFO(str))); \
        }                                                               \
    } while (0)
