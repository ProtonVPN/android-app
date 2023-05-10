//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2022 OpenVPN Inc.
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
