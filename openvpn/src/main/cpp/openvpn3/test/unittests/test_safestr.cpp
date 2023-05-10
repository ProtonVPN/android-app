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

#include "test_common.h"

#include <openvpn/buffer/safestr.hpp>

using namespace openvpn;

static void compare(const SafeString &s1, const SafeString &s2, const bool expect_eq)
{
    ASSERT_EQ(s1 == s2.c_str(), expect_eq);
    ASSERT_NE(s1 != s2.c_str(), expect_eq);
    ASSERT_EQ(s1 == s2.to_string(), expect_eq);
    ASSERT_NE(s1 != s2.to_string(), expect_eq);
}

TEST(safestr, test_1)
{
    SafeString a("mybigsecret");
    SafeString b("mybigsekret");
    SafeString c("mybigsekrets");
    SafeString a2("mybigsecret");

    compare(a, a2, true);
    compare(a2, a, true);
    compare(a, b, false);
    compare(a, c, false);
    compare(b, c, false);
    compare(b, a, false);
    compare(c, a, false);
    compare(c, b, false);
}
