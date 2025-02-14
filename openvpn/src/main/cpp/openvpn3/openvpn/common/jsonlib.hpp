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

#if defined(HAVE_JSONCPP)
#define HAVE_JSON
#include "json/json.h" // JsonCpp library
#elif defined(HAVE_OPENVPN_COMMON)
#define HAVE_JSON
#define OPENVPN_JSON_INTERNAL
#include <openvpn/common/json.hpp> // internal OpenVPN JSON implementation
#endif
