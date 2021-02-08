//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2020 OpenVPN Inc.
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU Affero General Public License Version 3
//    as published by the Free Software Foundation.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU Affero General Public License for more details.
//
//    You should have received a copy of the GNU Affero General Public License
//    along with this program in the COPYING file.
//    If not, see <http://www.gnu.org/licenses/>.

#include "test_common.h"


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

namespace unittests
{  
  TEST(LogInfoTest, TestLogInfo)
  {    
    std::string msg("logMessage");
    openvpn::ClientAPI::LogInfo logInfo(msg);
    auto text = logInfo.text;

    ASSERT_EQ(text, msg);
  }
}  // namespace
