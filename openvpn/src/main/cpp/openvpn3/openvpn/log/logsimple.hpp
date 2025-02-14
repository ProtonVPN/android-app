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

// Define simple logging macros that simply output to stdout

#pragma once

#include <iostream>

#ifndef OPENVPN_LOG_STREAM
#define OPENVPN_LOG_STREAM std::cout
#endif

#define OPENVPN_LOG(args) OPENVPN_LOG_STREAM << args << std::endl

// like OPENVPN_LOG but no trailing newline
#define OPENVPN_LOG_NTNL(args) OPENVPN_LOG_STREAM << args << std::flush

#define OPENVPN_LOG_STRING(str) OPENVPN_LOG_STREAM << (str) << std::flush
