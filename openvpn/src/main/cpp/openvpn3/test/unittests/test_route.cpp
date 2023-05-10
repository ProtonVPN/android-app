#include "test_common.h"

#include <iostream>
#include <set>

#include <openvpn/common/exception.hpp>

#include <openvpn/addr/route.hpp>

using namespace openvpn;

void test_route_parse(const std::string &rstr, const std::string &expected, bool ipv6)
{
    const IP::Route r(rstr);
    ASSERT_EQ(r.to_string(), expected);

    if (ipv6)
    {
        const IP::Route6 r6(rstr);
        ASSERT_EQ(r6.to_string(), expected);
    }
    else
    {
        const IP::Route4 r4(rstr);
        ASSERT_EQ(r4.to_string(), expected);
    }
}

TEST(IPAddr, routeSet)
{
    std::set<IP::Route> routes;
    routes.emplace("1.2.3.4/24");
    routes.emplace("1.2.3.0/24");
    routes.emplace("1.2.3.2/24");
    routes.emplace("1.2.3.1/24");
    routes.emplace("128.0.0.0/1");
    routes.emplace("1:2:3:4:5:6:dead:beef/64");
    routes.emplace("1:2:3:4:5:6:dead:bead/64");

    std::stringstream out;
    for (const auto &r : routes)
        out << r.to_string() << std::endl;

    ASSERT_EQ("128.0.0.0/1\n"
              "1.2.3.0/24\n"
              "1.2.3.1/24\n"
              "1.2.3.2/24\n"
              "1.2.3.4/24\n"
              "1:2:3:4:5:6:dead:bead/64\n"
              "1:2:3:4:5:6:dead:beef/64\n",
              out.str());
}

template <typename LIST>
void test_split(const LIST &rtlist, const std::string &expected)
{
    typedef typename LIST::value_type RT;
    std::stringstream out;
    for (const auto &r : rtlist)
    {
        RT r1, r2;
        if (r.is_canonical() && r.split(r1, r2))
        {
            out << r << ' ' << r1 << ' ' << r2 << std::endl;
        }
    }
    ASSERT_EQ(expected, out.str());
}

TEST(IPAddr, routeList4)
{
    IP::Route4List routes;
    routes.emplace_back("1.2.3.4/24");
    routes.emplace_back("1.2.3.0/24");
    routes.emplace_back("1.2.3.2/24");
    routes.emplace_back("1.2.3.1/24");
    routes.emplace_back("128.0.0.0/1");
    // OPENVPN_LOG_NTNL(routes.to_string());
    ASSERT_FALSE(routes.contains(IPv4::Addr::from_string("100.1.2.3")));
    ASSERT_TRUE(routes.contains(IPv4::Addr::from_string("200.1.2.3")));

    test_split(routes, "1.2.3.0/24 1.2.3.0/25 1.2.3.128/25\n128.0.0.0/1 128.0.0.0/2 192.0.0.0/2\n");
}

TEST(IPAddr, routeList6)
{
    IP::Route6List routes;
    routes.emplace_back("1:2:3:4:5:6:dead:beef/64");
    routes.emplace_back("cafe:babe::/64");
    // OPENVPN_LOG_NTNL(routes.to_string());
    ASSERT_FALSE(routes.contains(IPv6::Addr::from_string("1111:2222:3333:4444:5555:6666:7777:8888")));
    ASSERT_TRUE(routes.contains(IPv6::Addr::from_string("cafe:babe:0:0:1111:2222:3333:4444")));
    test_split(routes, "cafe:babe::/64 cafe:babe::/65 cafe:babe:0:0:8000::/65\n");
}

TEST(IPAddr, routeList)
{
    IP::RouteList routes;
    routes.emplace_back("1.2.3.4/24");
    routes.emplace_back("1.2.3.0/24");
    routes.emplace_back("1.2.3.2/24");
    routes.emplace_back("1.2.3.1/24");
    routes.emplace_back("128.0.0.0/1");
    routes.emplace_back("1:2:3:4:5:6:dead:beef/64");
    routes.emplace_back("cafe:babe::/64");
    // OPENVPN_LOG_NTNL(routes.to_string());
    ASSERT_FALSE(routes.contains(IP::Addr::from_string("100.1.2.3")));
    ASSERT_TRUE(routes.contains(IP::Addr::from_string("200.1.2.3")));
    ASSERT_FALSE(routes.contains(IP::Addr::from_string("1111:2222:3333:4444:5555:6666:7777:8888")));
    ASSERT_TRUE(routes.contains(IP::Addr::from_string("cafe:babe:0:0:1111:2222:3333:4444")));

    test_split(routes, "1.2.3.0/24 1.2.3.0/25 1.2.3.128/25\n"
                       "128.0.0.0/1 128.0.0.0/2 192.0.0.0/2\n"
                       "cafe:babe::/64 cafe:babe::/65 cafe:babe:0:0:8000::/65\n");
}

TEST(IPAddr, parseRoutes)
{
    test_route_parse("1.2.3.4", "1.2.3.4/32", false);
    test_route_parse("192.168.4.0/24", "192.168.4.0/24", false);
    test_route_parse("fe80::6470:7dff:fea5:f360/64", "fe80::6470:7dff:fea5:f360/64", true);

    ASSERT_THROW(
        IP::Route("192.168.4.0/33"),
        std::exception);
}