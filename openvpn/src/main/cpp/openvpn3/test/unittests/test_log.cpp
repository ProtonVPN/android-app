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

#include "test_common.hpp"


// The ovpncli.cpp file is not all OPENVPN_EXTERN safe and totally breaks
// if included in two files. We probably need to fix this or rename this
// file test_ovpncli and do ALL test that require ovpncli in this file
// (or have multiple test suites)

// This file needs to included with OPENVPN_EXTERN still defined otherwise
// the include from ovpncli.cpp breaks with duplicate symbols
#include <openvpn/common/base64.hpp>


#include <client/ovpncli.cpp>


#include <string>
#include <sstream>

namespace unittests {
TEST(LogInfoTest, TestLogInfo)
{
    std::string msg("logMessage");
    openvpn::ClientAPI::LogInfo logInfo(msg);

    ASSERT_EQ(logInfo.text, msg);
}
} // namespace unittests
