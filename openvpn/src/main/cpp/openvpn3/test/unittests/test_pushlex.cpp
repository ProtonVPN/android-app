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

/**
   @file
   @brief Unit test for PushLex
*/

#include "test_common.hpp"

#include <openvpn/buffer/bufstr.hpp>
#include <openvpn/options/pushlex.hpp>

using namespace openvpn;

static std::string get_next(PushLex &pl)
{
    if (pl.defined())
        return pl.next();
    else
        return std::string();
}

// parse a PUSH_UPDATE message
TEST(pushlex, test_1)
{
    const std::string csv = "PUSH_UPDATE,route 10.9.0.0 255.255.0.0,route 8.8.8.8,route-ipv6 fd69::/64";
    PushLex pl(const_buf_from_string(csv), true);
    ASSERT_EQ(get_next(pl), "route 10.9.0.0 255.255.0.0");
    ASSERT_EQ(get_next(pl), "route 8.8.8.8");
    ASSERT_EQ(get_next(pl), "route-ipv6 fd69::/64");
    ASSERT_FALSE(pl.defined());
}

// parse a PUSH_UPDATE message with tortured StandardLex quoting
TEST(pushlex, test_2)
{
    const std::string csv = "PUSH_UPDATE,echo \"one,two,three\",,route 1.2.3.4,echo \\\",echo \"foo\",echo \\,,echo fin,";
    PushLex pl(const_buf_from_string(csv), true);
    ASSERT_EQ(get_next(pl), "echo \"one,two,three\"");
    ASSERT_EQ(get_next(pl), "");
    ASSERT_EQ(get_next(pl), "route 1.2.3.4");
    ASSERT_EQ(get_next(pl), "echo \\\"");
    ASSERT_EQ(get_next(pl), "echo \"foo\"");
    ASSERT_EQ(get_next(pl), "echo \\,");
    ASSERT_EQ(get_next(pl), "echo fin");
    ASSERT_EQ(get_next(pl), "");
    ASSERT_FALSE(pl.defined());
}

// test PushLex with discard_prefix == false
TEST(pushlex, test_3)
{
    const std::string csv = "PUSH_UPDATE,route 10.9.0.0 255.255.0.0,route 8.8.8.8,route-ipv6 fd69::/64";
    PushLex pl(const_buf_from_string(csv), false);
    ASSERT_EQ(get_next(pl), "PUSH_UPDATE"); // this is here because discard_prefix == false
    ASSERT_EQ(get_next(pl), "route 10.9.0.0 255.255.0.0");
    ASSERT_EQ(get_next(pl), "route 8.8.8.8");
    ASSERT_EQ(get_next(pl), "route-ipv6 fd69::/64");
    ASSERT_FALSE(pl.defined());
}

// test PushLex with a null message
TEST(pushlex, test_4)
{
    const std::string csv = "PUSH_UPDATE,";
    PushLex pl(const_buf_from_string(csv), true);
    ASSERT_FALSE(pl.defined());
}

// test PushLex with a null buffer
TEST(pushlex, test_5)
{
    ConstBuffer cbuf;
    PushLex pl(cbuf, true);
    ASSERT_FALSE(pl.defined());
}

// test that PushLex throws an exception when prefix
// is unrecognized
TEST(pushlex, test_exception_1)
{
    JY_EXPECT_THROW(
        {
            const std::string csv = "FOO,route 10.9.0.0 255.255.0.0,route 8.8.8.8,route-ipv6 fd69::/64";
            PushLex pl(const_buf_from_string(csv), true);
        },
        PushLex::pushlex_error,
        "pushlex_error: not a valid PUSH_x message [1]");
}

// test that PushLex throws an exception when prefix
// is not followed by a comma (",")
TEST(pushlex, test_exception_2)
{
    JY_EXPECT_THROW(
        {
            const std::string csv = "PUSH_FOO...";
            PushLex pl(const_buf_from_string(csv), true);
        },
        PushLex::pushlex_error,
        "pushlex_error: not a valid PUSH_x message [2]");
}
