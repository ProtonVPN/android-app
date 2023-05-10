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

#include <openvpn/client/optfilt.hpp>

using namespace openvpn;

const std::string nopull_options(
    "ip-win32\n"
    "tap-sleep\n"
    "block-ipv6\n"
    "client-nat\n"
    "register-dns\n"

    "dhcp-renew\n"
    "dhcp-option\n"
    "dhcp-release\n"
    "dhcp-pre-release\n"

    "route 1.2.3.4\n"
    "route 192.168.0.0 255.255.255.0\n"
    "route 10.0.0.0 255.0.0.0\n"
    "route-ipv6\n"
    "route-delay\n"
    "route-metric\n"
    "route-method\n"

    "redirect-gateway\n"
    "redirect-private\n");

const std::string pull_filter_options(
    "option1 arg1\n"
    "option1 arg2\n"
    "option2 \"arg with space\"\n"
    "option2 \"arg  with  more  space\"\n"
    "option3 arg1 arg2\n"
    "option3  arg1  arg2\n"
    "option10 something else\n");

TEST(PushedOptionsFilter, RouteNopullEnabled)
{
    OptionList cfg;
    cfg.parse_from_config("route-nopull", nullptr);
    cfg.update_map();

    PushedOptionsFilter route_nopull_enabled(cfg);
    const std::string extra_option("unfiltered-option");

    OptionList src;
    OptionList dst;

    testLog->startCollecting();
    src.parse_from_config(nopull_options + extra_option, nullptr);
    dst.extend(src, &route_nopull_enabled);
    std::string filter_output(testLog->stopCollecting());

    ASSERT_EQ(1u, dst.size())
        << "Too few options have been filtered by --route-nopull" << std::endl
        << filter_output;

    dst.update_map();
    ASSERT_TRUE(dst.exists(extra_option))
        << "The wrong options have been filtered by --route-nopull:" << std::endl
        << "expected: " << extra_option << " got: " << dst[0].ref(0) << std::endl
        << filter_output;
}

TEST(PushedOptionsFilter, RouteNopullDisabled)
{
    OptionList cfg;

    PushedOptionsFilter route_nopull_disabled(cfg);
    const std::string extra_option("unfiltered-option");

    OptionList src;
    OptionList dst;

    testLog->startCollecting();
    src.parse_from_config(nopull_options + extra_option, nullptr);
    dst.extend(src, &route_nopull_disabled);
    std::string filter_output(testLog->stopCollecting());

    ASSERT_EQ(src.size(), dst.size())
        << "Too many options have been filtered by --route-nopull" << std::endl
        << filter_output;
}

TEST(PushedOptionsFilter, PullFilterAcceptAll)
{
    OptionList cfg;
    cfg.parse_from_config("pull-filter accept option", nullptr);
    cfg.update_map();

    PushedOptionsFilter filter_none(cfg);

    OptionList src;
    OptionList dst;

    testLog->startCollecting();
    src.parse_from_config(pull_filter_options, nullptr);
    dst.extend(src, &filter_none);
    std::string filter_output(testLog->stopCollecting());

    ASSERT_EQ(src.size(), dst.size())
        << "Not all options have been accepted by --pull-filter" << std::endl
        << filter_output;
}

TEST(PushedOptionsFilter, PullFilterMalformedAction)
{
    OptionList cfg;
    cfg.parse_from_config("pull-filter myaction match", nullptr);
    cfg.update_map();

    ASSERT_THROW(PushedOptionsFilter x(cfg), option_error);
}

TEST(PushedOptionsFilter, PullFilterMalformedShort)
{
    OptionList cfg;
    cfg.parse_from_config("pull-filter ignore", nullptr);
    cfg.update_map();

    ASSERT_THROW(PushedOptionsFilter x(cfg), option_error);
}

TEST(PushedOptionsFilter, PullFilterMalformedLong)
{
    OptionList cfg;
    cfg.parse_from_config("pull-filter ignore one two", nullptr);
    cfg.update_map();

    ASSERT_THROW(PushedOptionsFilter x(cfg), option_error);
}

TEST(PushedOptionsFilter, PullFilterIgnoreAll)
{
    OptionList cfg;
    cfg.parse_from_config("pull-filter ignore option", nullptr);
    cfg.update_map();

    PushedOptionsFilter filter_all(cfg);

    OptionList src;
    OptionList dst;

    testLog->startCollecting();
    src.parse_from_config(pull_filter_options, nullptr);
    dst.extend(src, &filter_all);
    std::string filter_output(testLog->stopCollecting());

    ASSERT_EQ(0u, dst.size())
        << "Not all options have been ignored by --pull-filter" << std::endl
        << filter_output;
}

TEST(PushedOptionsFilter, PullFilterRejectOne)
{
    OptionList cfg;
    cfg.parse_from_config("pull-filter reject option10", nullptr);
    cfg.update_map();

    PushedOptionsFilter reject_opt10(cfg);

    OptionList src;
    OptionList dst;

    testLog->startCollecting();
    src.parse_from_config(pull_filter_options, nullptr);
    JY_EXPECT_THROW(dst.extend(src, &reject_opt10), Option::RejectedException, "option10")
    testLog->stopCollecting();
}

TEST(PushedOptionsFilter, PullFilterAcceptWhitespace)
{
    OptionList cfg;
    cfg.parse_from_config(
        "pull-filter accept \"option3 arg1 arg2\"\n"
        "pull-filter ignore option",
        nullptr);
    cfg.update_map();

    PushedOptionsFilter accept_opt3(cfg);

    OptionList src;
    OptionList dst;

    testLog->startCollecting();
    src.parse_from_config(pull_filter_options, nullptr);
    dst.extend(src, &accept_opt3);
    std::string filter_output(testLog->stopCollecting());

    ASSERT_EQ(2u, dst.size())
        << "Not all option3's have been accepted by --pull-filter" << std::endl
        << filter_output;
}

TEST(PushedOptionsFilter, PullFilterIgnoreQuotedWhitespace)
{
    OptionList cfg;
    cfg.parse_from_config(
        "pull-filter accept \"option2 \\\"arg with space\\\"\"\n"
        "pull-filter ignore option",
        nullptr);
    cfg.update_map();

    PushedOptionsFilter accept_opt2_single_space(cfg);

    OptionList src;
    OptionList dst;

    testLog->startCollecting();
    src.parse_from_config(pull_filter_options, nullptr);
    dst.extend(src, &accept_opt2_single_space);
    std::string filter_output(testLog->stopCollecting());

    ASSERT_EQ(1u, dst.size())
        << "Too many options have been accepted by --pull-filter" << std::endl
        << filter_output;

    dst.update_map();
    ASSERT_EQ(dst[0].ref(1), "arg with space")
        << "Too many options have been accepted by --pull-filter" << std::endl
        << filter_output;
}

TEST(PushedOptionsFilter, PullFilterOverrideRouteNopull)
{
    OptionList cfg;
    cfg.parse_from_config(
        "pull-filter ignore \"route 1.2.3.4\"\n"
        "pull-filter ignore route-\n"
        "route-nopull\n"
        "pull-filter accept route\n",
        nullptr);
    cfg.update_map();

    PushedOptionsFilter override_route_nopull(cfg);

    OptionList src;
    OptionList dst;

    testLog->startCollecting();
    src.parse_from_config(nopull_options, nullptr);
    dst.extend(src, &override_route_nopull);
    std::string filter_output(testLog->stopCollecting());

    ASSERT_EQ(2u, dst.size())
        << "Expected two route option to be accepted" << std::endl
        << filter_output;

    dst.update_map();
    ASSERT_EQ(dst[0].ref(0), "route")
        << dst[0].ref(0) << " instead of route option has been accepted" << std::endl
        << filter_output;
    ASSERT_EQ(dst[1].ref(0), "route")
        << dst[1].ref(0) << " instead of route option has been accepted" << std::endl
        << filter_output;
    ASSERT_EQ(3u, dst[0].size())
        << "The host route option has been accepted, expected network route" << std::endl
        << filter_output;
    ASSERT_EQ(3u, dst[1].size())
        << "The host route option has been accepted, expected network route" << std::endl
        << filter_output;
}

TEST(PushedOptionsFilter, RejectDnsServerPrioNegative)
{
    OptionList cfg;
    PushedOptionsFilter filter_static(cfg);

    const std::string opt = "dns server -1 address ::1";

    OptionList src;
    OptionList dst;
    src.parse_from_config(opt, nullptr);
    src.update_map();

    testLog->startCollecting();
    JY_EXPECT_THROW(dst.extend(src, &filter_static), option_error, opt)
    std::string filter_output(testLog->stopCollecting());
}
