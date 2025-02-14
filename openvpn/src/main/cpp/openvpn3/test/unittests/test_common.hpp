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

/* Without the asio.hpp include we get winsocket.h related errors
 * See also https://stackoverflow.com/questions/9750344/boostasio-winsock-and-winsock-2-compatibility-issue
 */
#include <openvpn/io/io.hpp>
#include "test_helper.hpp"
#include <gtest/gtest.h>
