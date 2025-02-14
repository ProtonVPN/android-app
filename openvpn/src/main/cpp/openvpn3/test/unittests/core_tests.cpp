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


#include <gtest/gtest.h>


// We also need to import here all header files that
// define globals so that consumers that use them despite
// requesting them not to be declared with OPENVPN_EXTERN

#include <asio.hpp>
#include "test_helper.hpp"
#include <openvpn/ssl/sslchoose.hpp>
#include <openvpn/init/initprocess.hpp>

openvpn::LogOutputCollector *testLog;

int main(int argc, char **argv)
{
    testLog = new openvpn::LogOutputCollector();
    ::testing::InitGoogleTest(&argc, argv);
    openvpn::InitProcess::Init init;
    auto ret = RUN_ALL_TESTS();

    delete testLog;
    return ret;
}
