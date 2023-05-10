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

/* Without doing this log dance core will not compile ... */

#include "test_common.h"

#include <openvpn/client/cliemuexr.hpp>


namespace unittests {
#define DEBUG_PRINT_ROUTES      \
    for (auto &rt : tb->routes) \
        std::cout << rt << std::endl;

/* Helper function for quick result comparision */
std::string join_string_vector_sorted(std::vector<std::string> vec, const char *const delim = ", ")
{
    std::sort(vec.begin(), vec.end());
    std::ostringstream res;
    std::copy(vec.begin(), vec.end(), std::ostream_iterator<std::string>(res, delim));
    return res.str();
}

/* Simple class that just records */
class TunBuilderMock : public openvpn::TunBuilderBase
{
  public:
    bool is_ipv6;
    TunBuilderMock(bool ipv6)
        : is_ipv6(ipv6)
    {
    }

    bool tun_builder_add_route(const std::string &address,
                               int prefix_length,
                               int metric,
                               bool ipv6) override
    {
        auto rt = address + "/" + std::to_string(prefix_length);
        routes.push_back(rt);
        routesAddr.push_back(openvpn::IP::Route(rt));
        return is_ipv6 == ipv6;
    }

    bool tun_builder_set_remote_address(const std::string &address, bool ipv6) override
    {
        addresses.push_back(address);
        return is_ipv6 == ipv6;
    }

    std::vector<std::string> addresses;
    std::vector<std::string> routes;
    std::vector<openvpn::IP::Route> routesAddr;

    bool containsIP(std::string ipaddr)
    {
        return containsIP(openvpn::IP::Addr(ipaddr));
    }

    bool containsIP(openvpn::IP::Addr ipaddr)
    {
        for (auto &rt : routesAddr)
        {
            if (rt.contains(ipaddr))
                return true;
        }
        return false;
    }
};

class RouteEmulationTest : public testing::Test
{
  protected:
    openvpn::IPVerFlags *ipflags;
    openvpn::EmulateExcludeRoute::Ptr emu;
    TunBuilderMock *tb;
    openvpn::OptionList emptyOptionList;

    RouteEmulationTest()
        : ipflags(nullptr), tb(nullptr)
    {
    }

    void teardown()
    {
        delete tb;
        delete ipflags;
    }

    void setup(bool ipv6, bool excludeServer, bool keepEmu = false)
    {
        teardown();

        tb = new TunBuilderMock(ipv6);

        ipflags = new openvpn::IPVerFlags(emptyOptionList,
                                          ipv6 ? openvpn::IP::Addr::V6_MASK : openvpn::IP::Addr::V4_MASK);

        if (!keepEmu)
        {
            openvpn::EmulateExcludeRouteFactory::Ptr factory(
                new openvpn::EmulateExcludeRouteFactoryImpl(excludeServer));

            emu = factory->new_obj();
        }
    }

    // Helper functions to make writing test suite a bit easier
    void inclRoute(const std::string &incRoute)
    {
        addRoute(true, incRoute);
    }
    void exclRoute(const std::string &exclRoute)
    {
        addRoute(false, exclRoute);
    }

    void addRoute(bool include, const std::string &route)
    {
        std::string ipstr = route.substr(0, route.find('/'));
        std::string mask = route.substr(route.find('/') + 1);
        emu->add_route(include, openvpn::IP::Addr(ipstr), std::stoi(mask));
    }

    void doEmulate(std::string serverip = "1.2.3.4")
    {
        emu->emulate(this->tb, *this->ipflags, openvpn::IP::Addr(serverip));
    }

    ~RouteEmulationTest()
    {
        teardown();
    }
};

TEST_F(RouteEmulationTest, ExcludeOneSubnet)
{
    setup(false, false);

    emu->add_default_routes(true, true);

    emu->add_route(false, openvpn::IP::Addr("192.168.100.0"), 24);

    doEmulate();

    ASSERT_EQ(tb->routes.size(), 24u);
}

TEST_F(RouteEmulationTest, ExcludeSubnetsNoDefault)
{
    setup(false, false);
    // include this net
    emu->add_route(true, openvpn::IP::Addr("10.20.0.0"), 16);

    // but not the first half
    emu->add_route(false, openvpn::IP::Addr("10.20.0.0"), 17);

    doEmulate();

    ASSERT_EQ(tb->routes.size(), 1u);
    ASSERT_EQ(tb->routes.at(0), "10.20.128.0/17");

    setup(true, false);

    emu->add_route(true, openvpn::IP::Addr("2500:1000::"), 32);
    // but not the first half
    emu->add_route(false, openvpn::IP::Addr("2500:1000:8000::"), 33);

    doEmulate();

    ASSERT_EQ(tb->routes.size(), 1u);
    ASSERT_EQ(tb->routes.at(0), "2500:1000::/33");
}

TEST_F(RouteEmulationTest, excludeServer)
{
    setup(false, true);
    emu->add_default_routes(true, true);
    doEmulate("1.2.3.4");

    ASSERT_EQ(tb->routes.size(), 32u);
    ASSERT_FALSE(tb->containsIP("1.2.3.4"));
    ASSERT_TRUE(tb->containsIP("1.2.3.5"));
    ASSERT_TRUE(tb->containsIP("1.2.3.3"));
    ASSERT_TRUE(tb->containsIP("4.3.2.1"));

    setup(true, true);
    emu->add_default_routes(true, true);
    doEmulate("::1.2.3.4");

    ASSERT_EQ(tb->routes.size(), 128u);
    ASSERT_FALSE(tb->containsIP("::1.2.3.4"));
    ASSERT_TRUE(tb->containsIP("::1.2.3.5"));
    ASSERT_TRUE(tb->containsIP("::1.2.3.3"));
    ASSERT_TRUE(tb->containsIP("::4.3.2.1"));
}

TEST_F(RouteEmulationTest, nestedIPRoutes)
{
    // This sets up a number of routes that are all included in each

    setup(false, false);
    inclRoute("192.64.0.0/16");
    // second quarter.
    exclRoute("192.64.64.0/18");
    // last quarter
    inclRoute("192.64.112.0/20");
    // first quarter
    exclRoute("192.64.112.0/22");
    // first quarter again
    inclRoute("192.64.112.0/24");
    // second quarter
    exclRoute("192.64.112.64/26");

    doEmulate();

    // Excluded by 192.64.112.64/26
    ASSERT_FALSE(tb->containsIP("192.64.112.64"));
    ASSERT_FALSE(tb->containsIP("192.64.112.87"));

    // Included by 192.64.112.0/24
    ASSERT_TRUE(tb->containsIP("192.64.112.5"));
    ASSERT_TRUE(tb->containsIP("192.64.112.129"));
    ASSERT_TRUE(tb->containsIP("192.64.112.255"));

    // Excluded by 192.64.112.0/22
    ASSERT_FALSE(tb->containsIP("192.64.113.91"));
    ASSERT_FALSE(tb->containsIP("192.64.114.92"));
    ASSERT_FALSE(tb->containsIP("192.64.115.93"));


    // Included by 192.64.112.0/20
    ASSERT_TRUE(tb->containsIP("192.64.116.94"));
    ASSERT_TRUE(tb->containsIP("192.64.123.95"));


    // Excluded by 192.64.64.0/18"
    ASSERT_FALSE(tb->containsIP("192.64.64.96"));
    ASSERT_FALSE(tb->containsIP("192.64.97.98"));
    ASSERT_FALSE(tb->containsIP("192.64.111.99"));

    // included in 192.64.0.0/16
    ASSERT_TRUE(tb->containsIP("192.64.0.0"));
    ASSERT_TRUE(tb->containsIP("192.64.1.2"));

    // Not in the at all
    ASSERT_FALSE(tb->containsIP("1.2.3.4"));
    ASSERT_FALSE(tb->containsIP("192.63.255.255"));
    ASSERT_FALSE(tb->containsIP("192.65.0.0"));
    ASSERT_FALSE(tb->containsIP("128.0.0.0"));
    ASSERT_FALSE(tb->containsIP("192.0.0.0"));
    ASSERT_FALSE(tb->containsIP("255.255.255.255"));
}

TEST_F(RouteEmulationTest, DefaultRoute)
{
    setup(false, false);

    emu->add_default_routes(true, true);

    doEmulate();

    ASSERT_EQ(tb->routes.size(), 1u);
    ASSERT_EQ(tb->routes.at(0), "0.0.0.0/0");

    // Now something more tricky add unnecessary extra route
    // to confuse our emulation layer
    setup(false, false, true);

    inclRoute("192.168.0.0/24");

    doEmulate();

    ASSERT_EQ(tb->routes.size(), 2u);
    ASSERT_EQ(tb->routes.at(0), "0.0.0.0/0");
}

} // namespace unittests
