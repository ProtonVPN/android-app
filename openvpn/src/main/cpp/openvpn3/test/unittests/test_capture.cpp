#include "test_common.hpp"
#include "test_generators.hpp"
#include <rapidcheck/state.h>

#include <iostream>

#include <openvpn/common/file.hpp>
#include <openvpn/tun/builder/capture.hpp>

using namespace openvpn;

TEST(misc, capture)
{
    DnsServer server;
    server.addresses = {{{"8.8.8.8"}, 0}, {{"8.8.4.4"}, 53}};
    DnsOptions dns_options;
    dns_options.servers[0] = std::move(server);
    dns_options.search_domains = {{"yonan.net"}, {"openvpn.net"}};

    TunBuilderCapture::Ptr tbc(new TunBuilderCapture);

    tbc->tun_builder_set_remote_address("52.7.171.249", false);
    tbc->tun_builder_add_address("1.2.3.4", 24, "10.10.0.1", false, false);
    tbc->tun_builder_add_address("fe80::c32:4ff:febf:97d9", 64, "9999::7777", true, false);
    tbc->tun_builder_reroute_gw(true, false, 123);
    tbc->tun_builder_add_route("192.168.0.0", 16, 33, false);
    tbc->tun_builder_add_route("10.0.0.0", 8, -1, false);
    tbc->tun_builder_add_route("2000::", 4, 55, true);
    // tbc->tun_builder_add_route("X000::", 4, -1, true); // fixme
    tbc->tun_builder_add_route("3000::", 4, -1, true);
    tbc->tun_builder_add_route("fc00::", 7, 66, true);
    tbc->tun_builder_exclude_route("10.10.0.0", 24, 77, false);
    tbc->tun_builder_exclude_route("::1", 128, -1, true);
    tbc->tun_builder_set_dns_options(dns_options);
    tbc->tun_builder_set_mtu(1500);
    tbc->tun_builder_set_session_name("onewaytickettothemoon");
    tbc->tun_builder_add_proxy_bypass("bypass.example.com");
    tbc->tun_builder_set_proxy_auto_config_url("http://wpad.yonan.net/");
    tbc->tun_builder_set_proxy_http("foo.bar.gov", 1234);
    tbc->tun_builder_set_proxy_https("zoo.bar.gov", 4321);
    tbc->tun_builder_add_wins_server("6.6.6.6");
    tbc->tun_builder_add_wins_server("7.7.7.7");
    tbc->tun_builder_set_allow_family(AF_INET6, true);

    // OPENVPN_LOG("TEXT #1:\n" << tbc->to_string());

    // const std::string fn1 = "cap1.txt";
    Json::Value j1 = tbc->to_json();
    const std::string j1_txt = j1.toStyledString();

    // OPENVPN_LOG("writing to " << fn1);

    /// write_string(fn1, j1_txt);
    // OPENVPN_LOG("JSON #1:\n" << j1_txt);

    // const std::string fn2 = "cap2.txt";
    TunBuilderCapture::Ptr tbc2 = TunBuilderCapture::from_json(j1);
    tbc2->validate();
    Json::Value j2 = tbc2->to_json();
    const std::string j2_txt = j2.toStyledString();
    // OPENVPN_LOG("writing to " << fn2);
    // write_string(fn2, j2_txt);
    // OPENVPN_LOG("JSON #2:\n" << j2_txt);

    ASSERT_EQ(j1_txt, j2_txt) << "round trip failed";
}

//  ===============================================================================================
//  RemoteAddress tests
//  ===============================================================================================

TEST(RemoteAddress, EmptyIsNotDefined)
{
    const TunBuilderCapture::RemoteAddress remote_address;
    ASSERT_FALSE(remote_address.defined());
}

RC_GTEST_PROP(RemoteAddress, NonEmptyIsDefined, ())
{
    const auto address = *rc::gen::nonEmpty<std::string>();
    TunBuilderCapture::RemoteAddress remote_address;
    remote_address.address = address;
    RC_ASSERT(remote_address.defined());
}

TEST(RemoteAddress, EmptyStringRepresentation)
{
    const TunBuilderCapture::RemoteAddress remote_address;
    ASSERT_TRUE(remote_address.to_string().empty());
}

TEST(RemoteAddress, EmptyStringRepresentationIncludesIPv6Setting)
{
    TunBuilderCapture::RemoteAddress remote_address;
    remote_address.ipv6 = true;
    ASSERT_EQ(remote_address.to_string(), " [IPv6]");
}

RC_GTEST_PROP(RemoteAddress, StringRepresentationReturnsAddress, (const std::string &address))
{
    TunBuilderCapture::RemoteAddress remote_address;
    remote_address.address = address;
    RC_ASSERT(remote_address.to_string() == address);
}

RC_GTEST_PROP(RemoteAddress, StringRepresentationIncludesIPv6Setting, (const std::string &address))
{
    TunBuilderCapture::RemoteAddress remote_address;
    remote_address.ipv6 = true;
    remote_address.address = address;
    RC_ASSERT(remote_address.to_string() == address + " [IPv6]");
}

RC_GTEST_PROP(RemoteAddress, EmptyThrowsOnValidation, (const std::string &title))
{
    const TunBuilderCapture::RemoteAddress remote_address;
    RC_ASSERT_THROWS_AS(remote_address.validate(title), openvpn::IP::ip_exception);
}

RC_GTEST_PROP(RemoteAddress, ValidatesIPv4, (const std::string &title))
{
    TunBuilderCapture::RemoteAddress remote_address;
    remote_address.address = *rc::IPv4Address().as("Valid IPv4 address");
    remote_address.validate(title);
}

RC_GTEST_PROP(RemoteAddress, ValidatesIPv6, (const std::string &title))
{
    TunBuilderCapture::RemoteAddress remote_address;
    remote_address.address = *rc::IPv6Address().as("Valid IPv6 address");
    // Assumption: you have to specify manually and don't forget to set .ipv6 or else it throws
    remote_address.ipv6 = true;
    remote_address.validate(title);
}

RC_GTEST_PROP(RemoteAddress, ThrowsValidatingMismatchedIPVersion, (const std::string &title, bool ipv6))
{
    TunBuilderCapture::RemoteAddress remote_address;
    // Intentionally generate IP Address with mismatched version: IPv4 if ipv6 is true, IPv6 otherwise
    remote_address.address = ipv6 ? *rc::IPv4Address().as("Valid IPv4 address") : *rc::IPv6Address().as("Valid IPv6 address");
    // Assumption: you have to specify manually
    remote_address.ipv6 = ipv6;
    RC_ASSERT_THROWS_AS(remote_address.validate(title), openvpn::IP::ip_exception);
}

RC_GTEST_PROP(RemoteAddress, ThrowsValidatingInvalidIP, (const std::string &title, bool ipv6))
{
    TunBuilderCapture::RemoteAddress remote_address;
    remote_address.address = ipv6 ? *rc::IPv6Address(false).as("Invalid IPv6 address") : *rc::IPv4Address(false).as("Invalid IPv4 address");
    // Assumption: you have to specify manually
    remote_address.ipv6 = ipv6;
    RC_ASSERT_THROWS_AS(remote_address.validate(title), openvpn::IP::ip_exception);
}

RC_GTEST_PROP(RemoteAddress, EmptyJsonRoundTripHaveSameStringRepresentation, (const std::string &title))
{
    const TunBuilderCapture::RemoteAddress remote_address;
    const auto address_as_json = remote_address.to_json();
    TunBuilderCapture::RemoteAddress from_json;
    from_json.from_json(address_as_json, title);
    RC_ASSERT(remote_address.to_string() == from_json.to_string());
}

RC_GTEST_PROP(RemoteAddress, EmptyJsonRoundTripHaveSameDefinedStatus, (const std::string &title))
{
    const TunBuilderCapture::RemoteAddress remote_address;
    const auto address_as_json = remote_address.to_json();
    TunBuilderCapture::RemoteAddress from_json;
    from_json.from_json(address_as_json, title);
    RC_ASSERT(remote_address.defined() == from_json.defined());
}

RC_GTEST_PROP(RemoteAddress, EmptyJsonRoundTripThrowsOnValidation, (const std::string &title))
{
    const TunBuilderCapture::RemoteAddress remote_address;
    RC_ASSERT_THROWS_AS(remote_address.validate(title), openvpn::IP::ip_exception);
    const auto address_as_json = remote_address.to_json();
    TunBuilderCapture::RemoteAddress from_json;
    from_json.from_json(address_as_json, title);
    RC_ASSERT_THROWS_AS(from_json.validate(title), openvpn::IP::ip_exception);
}

RC_GTEST_PROP(RemoteAddress, JsonRoundTripHaveSameStringRepresentation, (const std::string &address, const std::string &title, bool ipv6))
{
    TunBuilderCapture::RemoteAddress remote_address;
    remote_address.ipv6 = ipv6;
    remote_address.address = address;
    const auto address_as_json = remote_address.to_json();
    TunBuilderCapture::RemoteAddress from_json;
    from_json.from_json(address_as_json, title);
    RC_ASSERT(remote_address.to_string() == from_json.to_string());
}

RC_GTEST_PROP(RemoteAddress, JsonRoundTripHaveSameDefinedStatus, (const std::string &title))
{
    const TunBuilderCapture::RemoteAddress remote_address;
    const auto address_as_json = remote_address.to_json();
    TunBuilderCapture::RemoteAddress from_json;
    from_json.from_json(address_as_json, title);
    RC_ASSERT(remote_address.defined() == from_json.defined());
}

RC_GTEST_PROP(RemoteAddress, JsonRoundTripThrowsValidatingMismatchedIPVersion, (const std::string &title, bool ipv6))
{
    TunBuilderCapture::RemoteAddress remote_address;
    // Intentionally generate IP Address with mismatched version: IPv4 if ipv6 is true, IPv6 otherwise
    remote_address.address = ipv6 ? *rc::IPv4Address().as("Valid IPv4 address") : *rc::IPv6Address().as("Valid IPv6 address");
    remote_address.ipv6 = ipv6;
    RC_ASSERT_THROWS_AS(remote_address.validate(title), openvpn::IP::ip_exception);
    const auto address_as_json = remote_address.to_json();
    TunBuilderCapture::RemoteAddress from_json;
    from_json.from_json(address_as_json, title);
    RC_ASSERT_THROWS_AS(from_json.validate(title), openvpn::IP::ip_exception);
}

RC_GTEST_PROP(RemoteAddress, JsonRoundTripThrowsValidatingInvalidIP, (const std::string &title, bool ipv6))
{
    TunBuilderCapture::RemoteAddress remote_address;
    remote_address.address = ipv6 ? *rc::IPv6Address(false).as("Invalid IPv6 address") : *rc::IPv4Address(false).as("Invalid IPv4 address");
    remote_address.ipv6 = ipv6;
    RC_ASSERT_THROWS_AS(remote_address.validate(title), openvpn::IP::ip_exception);
    const auto address_as_json = remote_address.to_json();
    TunBuilderCapture::RemoteAddress from_json;
    from_json.from_json(address_as_json, title);
    RC_ASSERT_THROWS_AS(from_json.validate(title), openvpn::IP::ip_exception);
}

RC_GTEST_PROP(RemoteAddress, JsonRoundTripValidatesCorrectIP, (const std::string &title, bool ipv6))
{
    TunBuilderCapture::RemoteAddress remote_address;
    remote_address.address = ipv6 ? *rc::IPv6Address().as("Valid IPv6 address") : *rc::IPv4Address().as("Valid IPv4 address");
    remote_address.ipv6 = ipv6;
    remote_address.validate(title);
    const auto address_as_json = remote_address.to_json();
    TunBuilderCapture::RemoteAddress from_json;
    from_json.from_json(address_as_json, title);
    from_json.validate(title);
}

RC_GTEST_PROP(RemoteAddress, FromInvalidJsonDoesNotChangeOriginalObject, (const std::string &address, const std::string &title, bool ipv6))
{
    TunBuilderCapture::RemoteAddress from_json;
    from_json.ipv6 = ipv6;
    from_json.address = address;
    const Json::Value invalid_json;
    from_json.from_json(invalid_json, title);
    RC_ASSERT(from_json.ipv6 == ipv6);
    RC_ASSERT(from_json.address == address);
}

//  ===============================================================================================
//  RerouteGW tests
//  ===============================================================================================

TEST(RerouteGW, EmptyStringRepresentationReturnsUnsetOptions)
{
    constexpr TunBuilderCapture::RerouteGW reroute_gw;
    ASSERT_EQ(reroute_gw.to_string(), "IPv4=0 IPv6=0 flags=[ ]");
}

RC_GTEST_PROP(RerouteGW, StringRepresentationReturnsSetOptions, (bool ipv4, bool ipv6, rc::RedirectGatewayFlagsValues flags))
{
    TunBuilderCapture::RerouteGW reroute_gw;
    reroute_gw.ipv4 = ipv4;
    reroute_gw.ipv6 = ipv6;
    reroute_gw.flags = flags;
    // TODO: refactor original code so there's no need to rewrite method
    std::string ret;
    ret += "[ ";
    if (flags & RedirectGatewayFlags::RG_ENABLE)
        ret += "ENABLE ";
    if (flags & RedirectGatewayFlags::RG_REROUTE_GW)
        ret += "REROUTE_GW ";
    if (flags & RedirectGatewayFlags::RG_LOCAL)
        ret += "LOCAL ";
    if (flags & RedirectGatewayFlags::RG_AUTO_LOCAL)
        ret += "AUTO_LOCAL ";
    if (flags & RedirectGatewayFlags::RG_DEF1)
        ret += "DEF1 ";
    if (flags & RedirectGatewayFlags::RG_BYPASS_DHCP)
        ret += "BYPASS_DHCP ";
    if (flags & RedirectGatewayFlags::RG_BYPASS_DNS)
        ret += "BYPASS_DNS ";
    if (flags & RedirectGatewayFlags::RG_BLOCK_LOCAL)
        ret += "BLOCK_LOCAL ";
    if (flags & RedirectGatewayFlags::RG_IPv4)
        ret += "IPv4 ";
    if (flags & RedirectGatewayFlags::RG_IPv6)
        ret += "IPv6 ";
    ret += "]";
    const std::string ipv4_and_ipv6_return_string = {"IPv4=" + std::to_string(ipv4) + " IPv6=" + std::to_string(ipv6) + " "};
    RC_ASSERT(reroute_gw.to_string() == ipv4_and_ipv6_return_string + "flags=" + ret);
}

RC_GTEST_PROP(RerouteGW, EmptyJsonRoundTripHaveSameStringRepresentation, (const std::string &title))
{
    constexpr TunBuilderCapture::RerouteGW reroute_gw;
    const auto reroute_gw_as_json = reroute_gw.to_json();
    TunBuilderCapture::RerouteGW from_json;
    from_json.from_json(reroute_gw_as_json, title);
    RC_ASSERT(reroute_gw.to_string() == from_json.to_string());
}

RC_GTEST_PROP(RerouteGW, JsonRoundTripHaveSameStringRepresentation, (bool ipv4, bool ipv6, rc::RedirectGatewayFlagsValues flags, const std::string &title))
{
    TunBuilderCapture::RerouteGW reroute_gw;
    reroute_gw.ipv4 = ipv4;
    reroute_gw.ipv6 = ipv6;
    reroute_gw.flags = flags;
    const auto reroute_gw_as_json = reroute_gw.to_json();
    TunBuilderCapture::RerouteGW from_json;
    from_json.from_json(reroute_gw_as_json, title);
    RC_ASSERT(reroute_gw.to_string() == from_json.to_string());
}

RC_GTEST_PROP(RerouteGW, FromInvalidJsonThrows, (bool ipv4, bool ipv6, rc::RedirectGatewayFlagsValues flags, const std::string &title))
{
    TunBuilderCapture::RerouteGW from_json;
    from_json.ipv4 = ipv4;
    from_json.ipv6 = ipv6;
    from_json.flags = flags;
    const Json::Value invalid_json;
    RC_ASSERT_THROWS_AS(from_json.from_json(invalid_json, title), json::json_parse);
}

//  ===============================================================================================
//  RouteBased tests
//  ===============================================================================================

RC_GTEST_PROP(RouteBased, EmptyStringRepresentationReturnsUnsetPrefixLength, (rc::RouteBased route_based))
{
    std::visit(
        [](auto &&route_base_variant)
        { RC_ASSERT(route_base_variant.to_string() == "/0"); },
        route_based);
}

RC_GTEST_PROP(RouteBased, StringRepresentationReturnsSetOptions, (rc::RouteBased route_based, const std::string &address, unsigned char prefix_length, int metric, const std::string &gateway, bool ipv6, bool net30))
{
    std::visit(
        [&address, prefix_length, metric, &gateway, ipv6, net30](auto &&route_base_variant)
        {
            route_base_variant.address = address;
            route_base_variant.prefix_length = prefix_length;
            route_base_variant.metric = metric;
            route_base_variant.gateway = gateway;
            route_base_variant.ipv6 = ipv6;
            route_base_variant.net30 = net30;
            std::string output;
            output += address + "/" + std::to_string(prefix_length);
            if (!gateway.empty())
                output += " -> " + gateway;
            if (metric >= 0)
                output += " [METRIC=" + std::to_string(metric) + "]";
            if (ipv6)
                output += " [IPv6]";
            if (net30)
                output += " [net30]";
            RC_ASSERT(route_base_variant.to_string() == output);
        },
        route_based);
}

RC_GTEST_PROP(RouteBased, EmptyThrowsOnValidation, (rc::RouteBased route_based, const std::string &title))
{
    std::visit(
        [&title](auto &&route_base_variant)
        {
            using T = std::decay_t<decltype(route_base_variant)>;
            if constexpr (std::is_same_v<T, TunBuilderCapture::RouteBase>)
            {
                RC_DISCARD("RouteBase does not have public validate method");
            }
            else
            {
                RC_ASSERT_THROWS_AS(route_base_variant.validate(title), openvpn::IP::ip_exception);
            }
        },
        route_based);
}

RC_GTEST_PROP(RouteBased, Validates, (rc::RouteBased route_based, bool ipv6, bool net30, const std::string &title))
{
    // TODO: move to generator
    std::visit(
        [ipv6, net30, &title](auto &&route_base_variant)
        {
            using T = std::decay_t<decltype(route_base_variant)>;
            if constexpr (std::is_same_v<T, TunBuilderCapture::RouteBase>)
            {
                RC_DISCARD("RouteBase does not have public validate method");
            }
            else
            {
                // Performs canonicalization so Route is valid
                // TODO: separate path for RouteAddress that can be not canonical
                if (ipv6)
                {
                    route_base_variant.ipv6 = true;
                    auto ipv6_route = route_from_string(*rc::IPv6Address(), title, IP::Addr::V6);
                    ipv6_route.force_canonical();
                    route_base_variant.address = ipv6_route.to_string_optional_prefix_len();
                    route_base_variant.prefix_length = IPv6::Addr::SIZE;
                }
                else
                {
                    route_base_variant.ipv6 = false;
                    route_base_variant.address = *rc::IPv4Address().as("Valid IPv4 address");
                    auto [prefix_min, prefix_max] = rc::calculateIPPrefixRange(route_base_variant.address);
                    if (net30 && prefix_min <= 30 && prefix_max >= 30)
                    {
                        route_base_variant.net30 = true;
                        route_base_variant.prefix_length = 30;
                    }
                    else
                    {
                        route_base_variant.net30 = false;
                        route_base_variant.prefix_length = *rc::gen::inRange(static_cast<char>(prefix_min), static_cast<char>(prefix_max + 1)).as("Valid prefix length");
                    }
                }
                if (auto maybe_metric = *rc::gen::maybe(rc::gen::arbitrary<int>().as("Metric value")).as("Maybe metric"))
                {
                    route_base_variant.metric = *maybe_metric;
                }
                if (auto maybe_gateway = ipv6 ? *rc::gen::maybe(rc::IPv6Address().as("Valid IPv6 gateway")) : *rc::gen::maybe(rc::IPv4Address().as("Valid IPv4 gateway")))
                {
                    route_base_variant.gateway = *maybe_gateway;
                }
                route_base_variant.validate(title);
            }
        },
        route_based);
}


RC_GTEST_PROP(RouteBased, EmptyJsonRoundTripHaveSameStringRepresentation, (rc::RouteBased route_based, const std::string &title))
{
    std::visit(
        [&title](auto &&route_base_variant)
        {
            const auto route_based_as_json = route_base_variant.to_json();
            using T = std::decay_t<decltype(route_base_variant)>;
            T from_json;
            from_json.from_json(route_based_as_json, title);
            RC_ASSERT(route_base_variant.to_string() == from_json.to_string());
        },
        route_based);
}

RC_GTEST_PROP(RouteBased, JsonRoundTripHaveSameStringRepresentation, (rc::RouteBased route_based, const std::string &address, unsigned char prefix_length, int metric, const std::string &gateway, bool ipv6, bool net30, const std::string &title))
{
    std::visit(
        [&address, prefix_length, metric, &gateway, ipv6, net30, &title](auto &&route_base_variant)
        {
            route_base_variant.address = address;
            route_base_variant.prefix_length = prefix_length;
            route_base_variant.metric = metric;
            route_base_variant.gateway = gateway;
            route_base_variant.ipv6 = ipv6;
            route_base_variant.net30 = net30;
            const auto route_based_as_json = route_base_variant.to_json();
            using T = std::decay_t<decltype(route_base_variant)>;
            T from_json;
            from_json.from_json(route_based_as_json, title);
            RC_ASSERT(route_base_variant.to_string() == from_json.to_string());
        },
        route_based);
}


RC_GTEST_PROP(RouteBased, FromInvalidJsonThrows, (rc::RouteBased route_based, const std::string &title))
{
    std::visit(
        [&title](auto &&route_base_variant)
        {
            const Json::Value invalid_json = {};
            RC_ASSERT_THROWS_AS(route_base_variant.from_json(invalid_json, title), json::json_parse);
        },
        route_based);
}

//  ===============================================================================================
//  ProxyBypass tests
//  ===============================================================================================

TEST(ProxyBypass, EmptyIsNotDefined)
{
    const TunBuilderCapture::ProxyBypass proxy_bypass;
    ASSERT_FALSE(proxy_bypass.defined());
}

RC_GTEST_PROP(ProxyBypass, NonEmptyIsDefined, ())
{
    const auto bypass_host = *rc::gen::nonEmpty<std::string>();
    TunBuilderCapture::ProxyBypass proxy_bypass;
    proxy_bypass.bypass_host = bypass_host;
    RC_ASSERT(proxy_bypass.defined());
}

TEST(ProxyBypass, EmptyStringRepresentation)
{
    const TunBuilderCapture::ProxyBypass proxy_bypass;
    ASSERT_TRUE(proxy_bypass.to_string().empty());
}

RC_GTEST_PROP(ProxyBypass, StringRepresentationReturnBypassHost, (const std::string &bypass_host))
{
    TunBuilderCapture::ProxyBypass proxy_bypass;
    proxy_bypass.bypass_host = bypass_host;
    RC_ASSERT(proxy_bypass.to_string() == bypass_host);
}

RC_GTEST_PROP(ProxyBypass, EmptyValidates, (const std::string &title))
{
    const TunBuilderCapture::ProxyBypass proxy_bypass;
    proxy_bypass.validate(title);
}

RC_GTEST_PROP(ProxyBypass, EmptyJsonRoundTripHaveSameStringRepresentation, (const std::string &title))
{
    const TunBuilderCapture::ProxyBypass proxy_bypass;
    const auto proxy_bypass_as_json = proxy_bypass.to_json();
    TunBuilderCapture::ProxyBypass from_json;
    from_json.from_json(proxy_bypass_as_json, title);
    RC_ASSERT(proxy_bypass.to_string() == from_json.to_string());
}

RC_GTEST_PROP(ProxyBypass, EmptyJsonRoundTripHaveSameDefinedStatus, (const std::string &title))
{
    const TunBuilderCapture::ProxyBypass proxy_bypass;
    const auto proxy_bypass_as_json = proxy_bypass.to_json();
    TunBuilderCapture::ProxyBypass from_json;
    from_json.from_json(proxy_bypass_as_json, title);
    RC_ASSERT(proxy_bypass.defined() == from_json.defined());
}

RC_GTEST_PROP(ProxyBypass, EmptyJsonRoundTripValidates, (const std::string &title))
{
    const TunBuilderCapture::ProxyBypass proxy_bypass;
    proxy_bypass.validate(title);
    const auto proxy_bypass_as_json = proxy_bypass.to_json();
    TunBuilderCapture::ProxyBypass from_json;
    from_json.from_json(proxy_bypass_as_json, title);
    from_json.validate(title);
}

RC_GTEST_PROP(ProxyBypass, JsonRoundTripHaveSameStringRepresentation, (const std::string &bypass_host, const std::string &title))
{
    TunBuilderCapture::ProxyBypass proxy_bypass;
    proxy_bypass.bypass_host = bypass_host;
    const auto proxy_bypass_as_json = proxy_bypass.to_json();
    TunBuilderCapture::ProxyBypass from_json;
    from_json.from_json(proxy_bypass_as_json, title);
    RC_ASSERT(proxy_bypass.to_string() == from_json.to_string());
}

RC_GTEST_PROP(ProxyBypass, JsonRoundTripHaveSameDefinedStatus, (const std::string &bypass_host, const std::string &title))
{
    TunBuilderCapture::ProxyBypass proxy_bypass;
    proxy_bypass.bypass_host = bypass_host;
    const auto proxy_bypass_as_json = proxy_bypass.to_json();
    TunBuilderCapture::ProxyBypass from_json;
    from_json.from_json(proxy_bypass_as_json, title);
    RC_ASSERT(proxy_bypass.defined() == from_json.defined());
}

RC_GTEST_PROP(ProxyBypass, FromInvalidJsonThrows, (const std::string &title))
{
    TunBuilderCapture::ProxyBypass from_json;
    const Json::Value invalid_json;
    RC_ASSERT_THROWS_AS(from_json.from_json(invalid_json, title), json::json_parse);
}

//  ===============================================================================================
//  ProxyAutoConfigURL tests
//  ===============================================================================================

TEST(ProxyAutoConfigURL, EmptyIsNotDefined)
{
    const TunBuilderCapture::ProxyAutoConfigURL proxy_autoconfig_url;
    ASSERT_FALSE(proxy_autoconfig_url.defined());
}

RC_GTEST_PROP(ProxyAutoConfigURL, NonEmptyIsDefined, ())
{
    const auto url = *rc::gen::nonEmpty<std::string>();
    TunBuilderCapture::ProxyAutoConfigURL proxy_autoconfig_url;
    proxy_autoconfig_url.url = url;
    RC_ASSERT(proxy_autoconfig_url.defined());
}

TEST(ProxyAutoConfigURL, EmptyStringRepresentation)
{
    const TunBuilderCapture::ProxyAutoConfigURL proxy_autoconfig_url;
    ASSERT_TRUE(proxy_autoconfig_url.to_string().empty());
}

RC_GTEST_PROP(ProxyAutoConfigURL, StringRepresentationReturnsURL, (const std::string &url))
{
    TunBuilderCapture::ProxyAutoConfigURL proxy_autoconfig_url;
    proxy_autoconfig_url.url = url;
    RC_ASSERT(proxy_autoconfig_url.to_string() == url);
}

RC_GTEST_PROP(ProxyAutoConfigURL, EmptyValidates, (const std::string &title))
{
    const TunBuilderCapture::ProxyAutoConfigURL proxy_autoconfig_url;
    proxy_autoconfig_url.validate(title);
}

RC_GTEST_PROP(ProxyAutoConfigURL, EmptyJsonRoundTripHaveSameStringRepresentation, (const std::string &title))
{
    const TunBuilderCapture::ProxyAutoConfigURL proxy_autoconfig_url;
    const auto proxy_autoconfig_url_as_json = proxy_autoconfig_url.to_json();
    TunBuilderCapture::ProxyAutoConfigURL from_json;
    from_json.from_json(proxy_autoconfig_url_as_json, title);
    RC_ASSERT(proxy_autoconfig_url.to_string() == from_json.to_string());
}

RC_GTEST_PROP(ProxyAutoConfigURL, EmptyJsonRoundTripHaveSameDefinedStatus, (const std::string &title))
{
    const TunBuilderCapture::ProxyAutoConfigURL proxy_autoconfig_url;
    const auto proxy_autoconfig_url_as_json = proxy_autoconfig_url.to_json();
    TunBuilderCapture::ProxyAutoConfigURL from_json;
    from_json.from_json(proxy_autoconfig_url_as_json, title);
    RC_ASSERT(proxy_autoconfig_url.defined() == from_json.defined());
}

RC_GTEST_PROP(ProxyAutoConfigURL, EmptyJsonRoundTripValidates, (const std::string &title))
{
    const TunBuilderCapture::ProxyAutoConfigURL proxy_autoconfig_url;
    proxy_autoconfig_url.validate(title);
    const auto proxy_autoconfig_url_as_json = proxy_autoconfig_url.to_json();
    TunBuilderCapture::ProxyAutoConfigURL from_json;
    from_json.from_json(proxy_autoconfig_url_as_json, title);
    from_json.validate(title);
}

RC_GTEST_PROP(ProxyAutoConfigURL, JsonRoundTripHaveSameStringRepresentation, (const std::string &url, const std::string &title))
{
    TunBuilderCapture::ProxyAutoConfigURL proxy_autoconfig_url;
    proxy_autoconfig_url.url = url;
    const auto proxy_autoconfig_url_as_json = proxy_autoconfig_url.to_json();
    TunBuilderCapture::ProxyAutoConfigURL from_json;
    from_json.from_json(proxy_autoconfig_url_as_json, title);
    RC_ASSERT(proxy_autoconfig_url.to_string() == from_json.to_string());
}

RC_GTEST_PROP(ProxyAutoConfigURL, JsonRoundTripHaveSameDefinedStatus, (const std::string &url, const std::string &title))
{
    TunBuilderCapture::ProxyAutoConfigURL proxy_autoconfig_url;
    proxy_autoconfig_url.url = url;
    const auto proxy_autoconfig_url_as_json = proxy_autoconfig_url.to_json();
    TunBuilderCapture::ProxyAutoConfigURL from_json;
    from_json.from_json(proxy_autoconfig_url_as_json, title);
    RC_ASSERT(proxy_autoconfig_url.defined() == from_json.defined());
}

RC_GTEST_PROP(ProxyAutoConfigURL, FromInvalidJsonDoesNotChangeOriginalObject, (const std::string &domain, const std::string &title))
{
    TunBuilderCapture::ProxyAutoConfigURL from_json;
    from_json.url = domain;
    const Json::Value invalid_json;
    from_json.from_json(invalid_json, title);
    RC_ASSERT(from_json.url == domain);
}

//  ===============================================================================================
//  ProxyHostPort tests
//  ===============================================================================================

TEST(ProxyHostPort, EmptyIsNotDefined)
{
    const TunBuilderCapture::ProxyHostPort proxy_host_port;
    ASSERT_FALSE(proxy_host_port.defined());
}

RC_GTEST_PROP(ProxyHostPort, NonEmptyIsDefined, ())
{
    const auto host = *rc::gen::nonEmpty<std::string>();
    TunBuilderCapture::ProxyHostPort proxy_host_port;
    proxy_host_port.host = host;
    RC_ASSERT(proxy_host_port.defined());
}

TEST(ProxyHostPort, EmptyStringRepresentationReturnsDefaultPort)
{
    const TunBuilderCapture::ProxyHostPort proxy_host_port;
    ASSERT_EQ(proxy_host_port.to_string(), std::string{" "} + std::to_string(proxy_host_port.port));
}

RC_GTEST_PROP(ProxyHostPort, StringRepresentationReturnsHostPort, (const std::string &host, const int port))
{
    TunBuilderCapture::ProxyHostPort proxy_host_port;
    proxy_host_port.host = host;
    proxy_host_port.port = port;
    RC_ASSERT(proxy_host_port.to_string() == host + std::string{" "} + std::to_string(port));
}

RC_GTEST_PROP(ProxyHostPort, EmptyValidates, (const std::string &title))
{
    const TunBuilderCapture::ProxyHostPort proxy_host_port;
    proxy_host_port.validate(title);
}

RC_GTEST_PROP(ProxyHostPort, EmptyJsonRoundTripHaveSameStringRepresentation, (const std::string &title))
{
    const TunBuilderCapture::ProxyHostPort proxy_host_port;
    const auto proxy_host_port_as_json = proxy_host_port.to_json();
    TunBuilderCapture::ProxyHostPort from_json;
    from_json.from_json(proxy_host_port_as_json, title);
    RC_ASSERT(proxy_host_port.to_string() == from_json.to_string());
}

RC_GTEST_PROP(ProxyHostPort, EmptyJsonRoundTripHaveSameDefinedStatus, (const std::string &title))
{
    const TunBuilderCapture::ProxyHostPort proxy_host_port;
    const auto proxy_host_port_as_json = proxy_host_port.to_json();
    TunBuilderCapture::ProxyHostPort from_json;
    from_json.from_json(proxy_host_port_as_json, title);
    RC_ASSERT(proxy_host_port.defined() == from_json.defined());
}

RC_GTEST_PROP(ProxyHostPort, EmptyJsonRoundTripValidates, (const std::string &title))
{
    const TunBuilderCapture::ProxyHostPort proxy_host_port;
    proxy_host_port.validate(title);
    const auto proxy_host_port_as_json = proxy_host_port.to_json();
    TunBuilderCapture::ProxyHostPort from_json;
    from_json.from_json(proxy_host_port_as_json, title);
    from_json.validate(title);
}

RC_GTEST_PROP(ProxyHostPort, JsonRoundTripHaveSameStringRepresentation, (const std::string &host, const int port, const std::string &title))
{
    TunBuilderCapture::ProxyHostPort proxy_host_port;
    proxy_host_port.host = host;
    proxy_host_port.port = port;
    const auto proxy_host_port_as_json = proxy_host_port.to_json();
    TunBuilderCapture::ProxyHostPort from_json;
    from_json.from_json(proxy_host_port_as_json, title);
    RC_ASSERT(proxy_host_port.to_string() == from_json.to_string());
}

RC_GTEST_PROP(ProxyHostPort, JsonRoundTripHaveSameDefinedStatus, (const std::string &host, const std::string &title))
{
    TunBuilderCapture::ProxyHostPort proxy_host_port;
    proxy_host_port.host = host;
    const auto proxy_host_port_as_json = proxy_host_port.to_json();
    TunBuilderCapture::ProxyHostPort from_json;
    from_json.from_json(proxy_host_port_as_json, title);
    RC_ASSERT(proxy_host_port.defined() == from_json.defined());
}

RC_GTEST_PROP(ProxyHostPort, FromInvalidJsonDoesNotChangeOriginalObject, (const std::string &host, const int port, const std::string &title))
{
    TunBuilderCapture::ProxyHostPort from_json;
    from_json.host = host;
    from_json.port = port;
    const Json::Value invalid_json;
    from_json.from_json(invalid_json, title);
    RC_ASSERT(from_json.host == host);
    RC_ASSERT(from_json.port == port);
}

//  ===============================================================================================
//  WINSServer tests
//  ===============================================================================================

TEST(WINSServer, EmptyStringRepresentation)
{
    const TunBuilderCapture::WINSServer wins_server;
    ASSERT_TRUE(wins_server.to_string().empty());
}

RC_GTEST_PROP(WINSServer, StringRepresentationReturnsAddress, (const std::string &address))
{
    TunBuilderCapture::WINSServer wins_server;
    wins_server.address = address;
    RC_ASSERT(wins_server.to_string() == address);
}

RC_GTEST_PROP(WINSServer, EmptyThrowsOnValidation, (const std::string &title))
{
    const TunBuilderCapture::WINSServer wins_server;
    RC_ASSERT_THROWS_AS(wins_server.validate(title), openvpn::IP::ip_exception);
}

RC_GTEST_PROP(WINSServer, ValidatesAddress, (const std::string &title))
{
    TunBuilderCapture::WINSServer wins_server;
    wins_server.address = *rc::IPv4Address().as("Valid IPv4 address");
    wins_server.validate(title);
}

RC_GTEST_PROP(WINSServer, ThrowsValidatingInvalidAddress, (const std::string &title))
{
    TunBuilderCapture::WINSServer wins_server;
    wins_server.address = *rc::IPv4Address(false).as("Invalid IPv4 address");
    RC_ASSERT_THROWS_AS(wins_server.validate(title), openvpn::IP::ip_exception);
}

RC_GTEST_PROP(WINSServer, EmptyJsonRoundTripHaveSameStringRepresentation, (const std::string &title))
{
    const TunBuilderCapture::WINSServer wins_server;
    const auto wins_server_as_json = wins_server.to_json();
    TunBuilderCapture::WINSServer from_json;
    from_json.from_json(wins_server_as_json, title);
    RC_ASSERT(wins_server.to_string() == from_json.to_string());
}

RC_GTEST_PROP(WINSServer, EmptyJsonRoundTripThrowsOnValidation, (const std::string &title))
{
    const TunBuilderCapture::WINSServer wins_server;
    RC_ASSERT_THROWS_AS(wins_server.validate(title), openvpn::IP::ip_exception);
    const auto wins_server_as_json = wins_server.to_json();
    TunBuilderCapture::WINSServer from_json;
    from_json.from_json(wins_server_as_json, title);
    RC_ASSERT_THROWS_AS(from_json.validate(title), openvpn::IP::ip_exception);
}

RC_GTEST_PROP(WINSServer, JsonRoundTripHaveSameStringRepresentation, (const std::string &address, const std::string &title))
{
    TunBuilderCapture::WINSServer wins_server;
    wins_server.address = address;
    const auto wins_server_as_json = wins_server.to_json();
    TunBuilderCapture::WINSServer from_json;
    from_json.from_json(wins_server_as_json, title);
    RC_ASSERT(wins_server.to_string() == from_json.to_string());
}

RC_GTEST_PROP(WINSServer, JsonRoundTripValidatesAddress, (const std::string &title))
{
    TunBuilderCapture::WINSServer wins_server;
    wins_server.address = *rc::IPv4Address().as("Valid IPv4 address");
    wins_server.validate(title);
    const auto wins_server_as_json = wins_server.to_json();
    TunBuilderCapture::WINSServer from_json;
    from_json.from_json(wins_server_as_json, title);
    from_json.validate(title);
}

RC_GTEST_PROP(WINSServer, JsonRoundTripThrowsValidatingInvalidIP, (const std::string &title))
{
    TunBuilderCapture::WINSServer wins_server;
    wins_server.address = *rc::IPv4Address(false).as("Invalid IPv4 address");
    RC_ASSERT_THROWS_AS(wins_server.validate(title), openvpn::IP::ip_exception);
    const auto wins_server_as_json = wins_server.to_json();
    TunBuilderCapture::WINSServer from_json;
    from_json.from_json(wins_server_as_json, title);
    RC_ASSERT_THROWS_AS(from_json.validate(title), openvpn::IP::ip_exception);
}

RC_GTEST_PROP(WINSServer, FromInvalidJsonThrows, (const std::string &title))
{
    TunBuilderCapture::WINSServer from_json;
    const Json::Value invalid_json;
    RC_ASSERT_THROWS_AS(from_json.from_json(invalid_json, title), json::json_parse);
}

//  ===============================================================================================
//  TunBuilderCapture tests
//  ===============================================================================================

RC_GTEST_PROP(TunBuilderCapture, SetsRemoteAddress, (const std::string &address, const bool ipv6))
{
    TunBuilderCapture::Ptr tbc(new TunBuilderCapture);
    RC_ASSERT(tbc->tun_builder_set_remote_address(address, ipv6));
    RC_ASSERT(tbc->remote_address.address == address);
    RC_ASSERT(tbc->remote_address.ipv6 == ipv6);
}

RC_GTEST_PROP(TunBuilderCapture, AddsAddress, (const std::string &address, const unsigned char prefix_length, const std::string &gateway, const bool ipv6, const bool net30))
{
    const TunBuilderCapture::Ptr tbc(new TunBuilderCapture);
    RC_ASSERT(tbc->tun_builder_add_address(address, prefix_length, gateway, ipv6, net30));
    const auto ip_version = ipv6 ? IP::Addr::V6 : IP::Addr::V4;
    const TunBuilderCapture::RouteAddress *vpn_address = tbc->vpn_ip(ip_version);
    RC_ASSERT(vpn_address->address == address);
    RC_ASSERT(vpn_address->prefix_length == prefix_length);
    RC_ASSERT(vpn_address->gateway == gateway);
    RC_ASSERT(vpn_address->net30 == net30);
}

RC_GTEST_PROP(TunBuilderCapture, SetsRerouteGW, (const bool ipv4, const bool ipv6, const unsigned int flags))
{
    const TunBuilderCapture::Ptr tbc(new TunBuilderCapture);
    RC_ASSERT(tbc->tun_builder_reroute_gw(ipv4, ipv6, flags));
    RC_ASSERT(tbc->reroute_gw.ipv4 == ipv4);
    RC_ASSERT(tbc->reroute_gw.ipv6 == ipv6);
    RC_ASSERT(tbc->reroute_gw.flags == flags);
}

RC_GTEST_PROP(TunBuilderCapture, SetsRouteMetricDefault, (const int metric))
{
    const TunBuilderCapture::Ptr tbc(new TunBuilderCapture);
    RC_ASSERT(tbc->tun_builder_set_route_metric_default(metric));
    RC_ASSERT(tbc->route_metric_default == metric);
}

RC_GTEST_PROP(TunBuilderCapture, AddsRoute, (const std::string &address, const unsigned char prefix_length, const bool ipv6))
{
    const TunBuilderCapture::Ptr tbc(new TunBuilderCapture);
    const auto metric = *rc::gen::positive<int>().as("Valid route metric");
    RC_ASSERT(tbc->tun_builder_add_route(address, prefix_length, metric, ipv6));
    const auto &added_route = tbc->add_routes.back();
    RC_ASSERT(added_route.address == address);
    RC_ASSERT(added_route.prefix_length == prefix_length);
    RC_ASSERT(added_route.metric == metric);
    RC_ASSERT(added_route.ipv6 == ipv6);
}

RC_GTEST_PROP(TunBuilderCapture, ExcludesRoute, (const std::string &address, const unsigned char prefix_length, const int metric, const bool ipv6))
{
    const TunBuilderCapture::Ptr tbc(new TunBuilderCapture);
    RC_ASSERT(tbc->tun_builder_exclude_route(address, prefix_length, metric, ipv6));
    const auto &excluded_route = tbc->exclude_routes.back();
    RC_ASSERT(excluded_route.address == address);
    RC_ASSERT(excluded_route.prefix_length == prefix_length);
    RC_ASSERT(excluded_route.metric == metric);
    RC_ASSERT(excluded_route.ipv6 == ipv6);
}

RC_GTEST_PROP(TunBuilderCapture, SetsDNSOptions, (const std::string &address, const unsigned int port, const std::string &search_domain))
{
    DnsServer server = {};
    server.addresses.push_back({address, port});
    DnsOptions dns_options = {};
    dns_options.servers[0] = std::move(server);
    dns_options.search_domains = {{search_domain}};
    const TunBuilderCapture::Ptr tbc(new TunBuilderCapture);
    RC_ASSERT(tbc->tun_builder_set_dns_options(dns_options));
    RC_ASSERT(tbc->dns_options.search_domains.back().domain == search_domain);
    RC_ASSERT(tbc->dns_options.servers.at(0).addresses.back().address == address);
}

RC_GTEST_PROP(TunBuilderCapture, SetsLayer, ())
{
    const TunBuilderCapture::Ptr tbc(new TunBuilderCapture);
    const auto layer = *rc::gen::element(3, 2, 0).as("Layer - 3, 2 or 0");
    RC_ASSERT(tbc->tun_builder_set_layer(layer));
    RC_ASSERT(tbc->layer.value() == layer);
}

RC_GTEST_PROP(TunBuilderCapture, SetsMTU, (const int mtu))
{
    const TunBuilderCapture::Ptr tbc(new TunBuilderCapture);
    RC_ASSERT(tbc->tun_builder_set_mtu(mtu));
    RC_ASSERT(tbc->mtu == mtu);
}

RC_GTEST_PROP(TunBuilderCapture, SetsSessionName, (const std::string &session_name))
{
    const TunBuilderCapture::Ptr tbc(new TunBuilderCapture);
    RC_ASSERT(tbc->tun_builder_set_session_name(session_name));
    RC_ASSERT(tbc->session_name == session_name);
}

RC_GTEST_PROP(TunBuilderCapture, AddsProxyBypass, (const std::string &bypass_host))
{
    const TunBuilderCapture::Ptr tbc(new TunBuilderCapture);
    RC_ASSERT(tbc->tun_builder_add_proxy_bypass(bypass_host));
    RC_ASSERT(tbc->proxy_bypass.back().bypass_host == bypass_host);
}

RC_GTEST_PROP(TunBuilderCapture, SetsProxyAutoConfigURL, (const std::string &url))
{
    const TunBuilderCapture::Ptr tbc(new TunBuilderCapture);
    RC_ASSERT(tbc->tun_builder_set_proxy_auto_config_url(url));
    RC_ASSERT(tbc->proxy_auto_config_url.url == url);
}

RC_GTEST_PROP(TunBuilderCapture, SetsProxyHTTP, (const std::string &host, const int port))
{
    const TunBuilderCapture::Ptr tbc(new TunBuilderCapture);
    RC_ASSERT(tbc->tun_builder_set_proxy_http(host, port));
    RC_ASSERT(tbc->http_proxy.host == host);
    RC_ASSERT(tbc->http_proxy.port == port);
}

RC_GTEST_PROP(TunBuilderCapture, SetsProxyHTTPS, (const std::string &host, const int port))
{
    const TunBuilderCapture::Ptr tbc(new TunBuilderCapture);
    RC_ASSERT(tbc->tun_builder_set_proxy_https(host, port));
    RC_ASSERT(tbc->https_proxy.host == host);
    RC_ASSERT(tbc->https_proxy.port == port);
}

RC_GTEST_PROP(TunBuilderCapture, AddsWINSServer, (const std::string &address))
{
    const TunBuilderCapture::Ptr tbc(new TunBuilderCapture);
    RC_ASSERT(tbc->tun_builder_add_wins_server(address));
    RC_ASSERT(tbc->wins_servers.back().address == address);
}

RC_GTEST_PROP(TunBuilderCapture, SetsAllowFamily, (const bool allow))
{
    const TunBuilderCapture::Ptr tbc(new TunBuilderCapture);
    const auto allow_family = *rc::gen::element(AF_INET, AF_INET6).as("Allow family - AF_INET or AF_INET6");
    RC_ASSERT(tbc->tun_builder_set_allow_family(allow_family, allow));
    if (allow_family == AF_INET)
    {
        RC_ASSERT_FALSE(tbc->block_ipv4 == allow);
    }
    else
    {
        RC_ASSERT_FALSE(tbc->block_ipv6 == allow);
    }
}

RC_GTEST_PROP(TunBuilderCapture, SetsAllowLocalDNS, (const bool allow))
{
    const TunBuilderCapture::Ptr tbc(new TunBuilderCapture);
    RC_ASSERT(tbc->tun_builder_set_allow_local_dns(allow));
    RC_ASSERT_FALSE(tbc->block_outside_dns == allow);
}

RC_GTEST_PROP(TunBuilderCapture, ResetsTunnelAddresses, (const std::string &address, const unsigned char prefix_length, const std::string &gateway, const bool ipv6, const bool net30))
{
    const TunBuilderCapture::Ptr tbc(new TunBuilderCapture);
    RC_ASSERT(tbc->tun_builder_add_address(address, prefix_length, gateway, ipv6, net30));
    RC_ASSERT(tbc->tun_builder_add_address(address, prefix_length, gateway, !ipv6, net30));
    RC_ASSERT_FALSE(tbc->tunnel_addresses.empty());
    RC_ASSERT(tbc->tunnel_address_index_ipv4 > -1);
    RC_ASSERT(tbc->tunnel_address_index_ipv6 > -1);
    tbc->reset_tunnel_addresses();
    RC_ASSERT(tbc->tunnel_addresses.empty());
    RC_ASSERT(tbc->tunnel_address_index_ipv4 == -1);
    RC_ASSERT(tbc->tunnel_address_index_ipv6 == -1);
}

RC_GTEST_PROP(TunBuilderCapture, ResetsDNSOptions, (const std::string &address, const unsigned int port, const std::string &search_domain))
{
    DnsServer server = {};
    server.addresses.push_back({address, port});
    DnsOptions dns_options = {};
    dns_options.servers[0] = std::move(server);
    dns_options.search_domains = {{search_domain}};
    const TunBuilderCapture::Ptr tbc(new TunBuilderCapture);
    RC_ASSERT(tbc->tun_builder_set_dns_options(dns_options));
    RC_ASSERT_FALSE(tbc->dns_options.to_string().empty());
    tbc->reset_dns_options();
    RC_ASSERT(tbc->dns_options.to_string() == "Values from dhcp-options: false\n");
}

RC_GTEST_PROP(TunBuilderCapture, ReturnsVPNIPv4, (const std::string &address, const unsigned char prefix_length, const std::string &gateway, const bool net30))
{
    const TunBuilderCapture::Ptr tbc(new TunBuilderCapture);
    RC_ASSERT(tbc->vpn_ipv4() == nullptr);
    RC_ASSERT(tbc->tun_builder_add_address(address, prefix_length, gateway, false, net30));
    const TunBuilderCapture::RouteAddress *vpn_address = tbc->vpn_ipv4();
    RC_ASSERT(vpn_address->address == address);
    RC_ASSERT(vpn_address->prefix_length == prefix_length);
    RC_ASSERT(vpn_address->gateway == gateway);
    RC_ASSERT(vpn_address->net30 == net30);
}

RC_GTEST_PROP(TunBuilderCapture, ReturnsVPNIPv6, (const std::string &address, const unsigned char prefix_length, const std::string &gateway, const bool net30))
{
    const TunBuilderCapture::Ptr tbc(new TunBuilderCapture);
    RC_ASSERT(tbc->vpn_ipv6() == nullptr);
    RC_ASSERT(tbc->tun_builder_add_address(address, prefix_length, gateway, true, net30));
    const TunBuilderCapture::RouteAddress *vpn_address = tbc->vpn_ipv6();
    RC_ASSERT(vpn_address->address == address);
    RC_ASSERT(vpn_address->prefix_length == prefix_length);
    RC_ASSERT(vpn_address->gateway == gateway);
    RC_ASSERT(vpn_address->net30 == net30);
}

RC_GTEST_PROP(TunBuilderCapture, ReturnsVPNIP, (const std::string &address, const unsigned char prefix_length, const std::string &gateway, const bool ipv6, const bool net30))
{
    const TunBuilderCapture::Ptr tbc(new TunBuilderCapture);
    RC_ASSERT(tbc->vpn_ip(IP::Addr::UNSPEC) == nullptr);
    RC_ASSERT(tbc->tun_builder_add_address(address, prefix_length, gateway, ipv6, net30));
    const auto ip_version = ipv6 ? IP::Addr::V6 : IP::Addr::V4;
    const TunBuilderCapture::RouteAddress *vpn_address = tbc->vpn_ip(ip_version);
    RC_ASSERT(vpn_address->address == address);
    RC_ASSERT(vpn_address->prefix_length == prefix_length);
    RC_ASSERT(vpn_address->gateway == gateway);
    RC_ASSERT(vpn_address->net30 == net30);
}

RC_GTEST_PROP(TunBuilderCapture, StringRepresentation, (const std::string &address, const unsigned char prefix_length, const std::string &gateway, const bool ipv6, const bool net30))
{
    const TunBuilderCapture::Ptr tbc(new TunBuilderCapture);
    std::ostringstream os = {};
    os << "Session Name: " << tbc->session_name << '\n';
    os << "Layer: " << tbc->layer.str() << '\n';
    os << "Remote Address: " << tbc->remote_address.to_string() << '\n';
    os << "Tunnel Addresses:\n";
    os << "Reroute Gateway: " << tbc->reroute_gw.to_string() << '\n';
    os << "Block IPv4: " << (tbc->block_ipv4 ? "yes" : "no") << '\n';
    os << "Block IPv6: " << (tbc->block_ipv6 ? "yes" : "no") << '\n';
    os << "Block local DNS: " << (tbc->block_outside_dns ? "yes" : "no") << '\n';
    os << "Add Routes:\n";
    os << "Exclude Routes:\n";
    RC_ASSERT(tbc->to_string() == os.str());
}

struct TunBuilderCaptureModel
{
    std::string session_name;
    int mtu{0};
    Layer layer{Layer::OSI_LAYER_3};
    TunBuilderCapture::RemoteAddress remote_address{};
    std::vector<TunBuilderCapture::RouteAddress> tunnel_addresses;
    int tunnel_address_index_ipv4{-1};
    int tunnel_address_index_ipv6{-1};
    TunBuilderCapture::RerouteGW reroute_gw{};
    bool block_ipv4{false};
    bool block_ipv6{false};
    bool block_outside_dns{false};
    int route_metric_default{-1};
    std::vector<TunBuilderCapture::Route> add_routes;
    std::vector<TunBuilderCapture::Route> exclude_routes;
    DnsOptions dns_options{};
    std::vector<TunBuilderCapture::ProxyBypass> proxy_bypass;
    TunBuilderCapture::ProxyAutoConfigURL proxy_auto_config_url{};
    TunBuilderCapture::ProxyHostPort http_proxy{};
    TunBuilderCapture::ProxyHostPort https_proxy{};
    std::vector<TunBuilderCapture::WINSServer> wins_servers{};
    static constexpr auto mtu_ipv4_maximum{65'535};
};

struct SetRemoteAddress final : rc::state::Command<TunBuilderCaptureModel, TunBuilderCapture>
{
    std::string address;
    bool ipv6{false};

    explicit SetRemoteAddress()
        : address{*rc::gen::arbitrary<std::string>()}, ipv6{*rc::gen::arbitrary<bool>()}
    {
    }

    auto apply(TunBuilderCaptureModel &model) const -> void override
    {
        model.remote_address.address = address;
        model.remote_address.ipv6 = ipv6;
    }

    auto run(const TunBuilderCaptureModel &model, TunBuilderCapture &sut) const -> void override
    {
        RC_ASSERT(sut.tun_builder_set_remote_address(address, ipv6));
        RC_ASSERT(sut.remote_address.address == address);
        RC_ASSERT(sut.remote_address.ipv6 == ipv6);
    }

    auto show(std::ostream &os) const -> void override
    {
        os << "Set RemoteAddress to " << address << " " << (ipv6 ? "IPv6" : "IPv4");
    }
};

struct AddAddress final : rc::state::Command<TunBuilderCaptureModel, TunBuilderCapture>
{
    std::string address;
    unsigned char prefix_length{0};
    int metric{-1};
    std::string gateway;
    bool ipv6{false};
    bool net30{false};

    explicit AddAddress()
        : address{*rc::gen::arbitrary<std::string>()}, prefix_length{*rc::gen::arbitrary<unsigned char>()}, gateway{*rc::gen::arbitrary<std::string>()}, ipv6{*rc::gen::arbitrary<bool>()}, net30{*rc::gen::arbitrary<bool>()}
    {
    }

    auto apply(TunBuilderCaptureModel &model) const -> void override
    {
        TunBuilderCapture::RouteAddress address;
        address.address = this->address;
        address.prefix_length = static_cast<unsigned char>(prefix_length);
        address.gateway = gateway;
        address.ipv6 = ipv6;
        address.net30 = net30;
        if (ipv6)
        {
            model.tunnel_address_index_ipv6 = static_cast<int>(model.tunnel_addresses.size());
        }
        else
        {
            model.tunnel_address_index_ipv4 = static_cast<int>(model.tunnel_addresses.size());
        }
        model.tunnel_addresses.push_back(std::move(address));
    }

    auto run(const TunBuilderCaptureModel &model, TunBuilderCapture &sut) const -> void override
    {
        RC_ASSERT(sut.tun_builder_add_address(address, prefix_length, gateway, ipv6, net30));
        RC_ASSERT(sut.tunnel_addresses.size() == model.tunnel_addresses.size() + 1);
        if (ipv6)
        {
            RC_ASSERT(sut.tunnel_address_index_ipv6 == static_cast<int>(model.tunnel_addresses.size()));
            const auto *current_address = sut.vpn_ipv6();
            RC_ASSERT(current_address->address == address);
            RC_ASSERT(current_address->prefix_length == prefix_length);
            RC_ASSERT(current_address->gateway == gateway);
            RC_ASSERT(current_address->ipv6 == ipv6);
            RC_ASSERT(current_address->net30 == net30);
        }
        else
        {
            RC_ASSERT(sut.tunnel_address_index_ipv4 == static_cast<int>(model.tunnel_addresses.size()));
            const auto *current_address = sut.vpn_ipv4();
            RC_ASSERT(current_address->address == address);
            RC_ASSERT(current_address->prefix_length == prefix_length);
            RC_ASSERT(current_address->gateway == gateway);
            RC_ASSERT(current_address->ipv6 == ipv6);
            RC_ASSERT(current_address->net30 == net30);
        }
        const auto *current_address = sut.vpn_ip(ipv6 ? IP::Addr::V6 : IP::Addr::V4);
        RC_ASSERT(current_address->address == address);
        RC_ASSERT(current_address->prefix_length == prefix_length);
        RC_ASSERT(current_address->gateway == gateway);
        RC_ASSERT(current_address->ipv6 == ipv6);
        RC_ASSERT(current_address->net30 == net30);
    }

    auto show(std::ostream &os) const -> void override
    {
        os << "Add address: " << address << " prefix_length: " << prefix_length << " gateway: " << gateway << (ipv6 ? "IPv6" : "IPv4") << " net30: " << net30;
    }
};

struct RerouteGW final : rc::state::Command<TunBuilderCaptureModel, TunBuilderCapture>
{
    bool ipv4{false};
    bool ipv6{false};
    unsigned int flags{0};

    explicit RerouteGW()
        : ipv4{*rc::gen::arbitrary<bool>()}, ipv6{*rc::gen::arbitrary<bool>()}, flags{*rc::gen::arbitrary<unsigned int>()}
    {
    }

    auto apply(TunBuilderCaptureModel &model) const -> void override
    {
        model.reroute_gw.ipv4 = ipv4;
        model.reroute_gw.ipv6 = ipv6;
        model.reroute_gw.flags = flags;
    }

    auto run(const TunBuilderCaptureModel &model, TunBuilderCapture &sut) const -> void override
    {
        RC_ASSERT(sut.tun_builder_reroute_gw(ipv4, ipv6, flags));
        RC_ASSERT(sut.reroute_gw.ipv6 == ipv6);
        RC_ASSERT(sut.reroute_gw.ipv4 == ipv4);
        RC_ASSERT(sut.reroute_gw.ipv6 == ipv6);
    }

    auto show(std::ostream &os) const -> void override
    {
        os << "Set RerouteGW ipv4: " << ipv4 << " ipv6: " << ipv6 << " flags: " << flags;
    }
};

struct SetRouteMetricDefault final : rc::state::Command<TunBuilderCaptureModel, TunBuilderCapture>
{
    int metric{};

    explicit SetRouteMetricDefault()
        : metric{*rc::gen::arbitrary<int>()}
    {
    }

    auto apply(TunBuilderCaptureModel &model) const -> void override
    {
        model.route_metric_default = metric;
    }

    auto run(const TunBuilderCaptureModel &model, TunBuilderCapture &sut) const -> void override
    {
        RC_ASSERT(sut.tun_builder_set_route_metric_default(metric));
        RC_ASSERT(sut.route_metric_default == metric);
    }

    auto show(std::ostream &os) const -> void override
    {
        os << "Set route metric default to " << metric;
    }
};

struct AddRoute final : rc::state::Command<TunBuilderCaptureModel, TunBuilderCapture>
{
    std::string address;
    unsigned char prefix_length{0};
    int metric{-1};
    std::string gateway;
    bool ipv6{false};
    bool net30{false};

    explicit AddRoute()
        : address{*rc::gen::arbitrary<std::string>()}, prefix_length{static_cast<unsigned char>(*rc::gen::arbitrary<int>())}, metric{*rc::gen::arbitrary<int>()}, ipv6{*rc::gen::arbitrary<bool>()}
    {
    }

    auto apply(TunBuilderCaptureModel &model) const -> void override
    {
        TunBuilderCapture::Route route;
        route.address = address;
        route.prefix_length = static_cast<unsigned char>(prefix_length);
        route.metric = (metric < 0 ? model.route_metric_default : metric);
        route.ipv6 = ipv6;
        model.add_routes.push_back(std::move(route));
    }

    auto run(const TunBuilderCaptureModel &model, TunBuilderCapture &sut) const -> void override
    {
        RC_ASSERT(sut.tun_builder_add_route(address, prefix_length, metric, ipv6));
        RC_ASSERT(sut.add_routes.size() == model.add_routes.size() + 1);
        const auto &added_route = sut.add_routes.back();
        RC_ASSERT(added_route.address == address);
        RC_ASSERT(added_route.prefix_length == prefix_length);
        RC_ASSERT(added_route.metric == (metric < 0 ? model.route_metric_default : metric));
        RC_ASSERT(added_route.ipv6 == ipv6);
    }

    auto show(std::ostream &os) const -> void override
    {
        os << "Add Route: " << address << " prefix length: " << prefix_length << " metric: " << metric << " ipv6: " << ipv6;
    }
};

struct ExcludeRoute final : rc::state::Command<TunBuilderCaptureModel, TunBuilderCapture>
{
    std::string address;
    unsigned char prefix_length{0};
    int metric{-1};
    std::string gateway;
    bool ipv6{false};
    bool net30{false};

    explicit ExcludeRoute()
        : address{*rc::gen::arbitrary<std::string>()}, prefix_length{static_cast<unsigned char>(*rc::gen::arbitrary<int>())}, metric{*rc::gen::arbitrary<int>()}, ipv6{*rc::gen::arbitrary<bool>()}
    {
    }

    auto apply(TunBuilderCaptureModel &model) const -> void override
    {
        TunBuilderCapture::Route route;
        route.address = address;
        route.prefix_length = static_cast<unsigned char>(prefix_length);
        route.metric = metric;
        route.ipv6 = ipv6;
        model.exclude_routes.push_back(std::move(route));
    }

    auto run(const TunBuilderCaptureModel &model, TunBuilderCapture &sut) const -> void override
    {
        RC_ASSERT(sut.tun_builder_exclude_route(address, prefix_length, metric, ipv6));
        RC_ASSERT(sut.exclude_routes.size() == model.exclude_routes.size() + 1);
        const auto &excluded_route = sut.exclude_routes.back();
        RC_ASSERT(excluded_route.address == address);
        RC_ASSERT(excluded_route.prefix_length == prefix_length);
        RC_ASSERT(excluded_route.metric == metric);
        RC_ASSERT(excluded_route.ipv6 == ipv6);
    }

    auto show(std::ostream &os) const -> void override
    {
        os << "Add Exclude Route: " << address << " prefix length: " << prefix_length << " metric: " << metric << " ipv6: " << ipv6;
    }
};

struct SetLayer final : rc::state::Command<TunBuilderCaptureModel, TunBuilderCapture>
{
    int layer{};

    explicit SetLayer() : layer{*rc::gen::elementOf<std::vector<int>>({0, 2, 3})}
    {
    }

    auto apply(TunBuilderCaptureModel &model) const -> void override
    {
        model.layer = Layer::from_value(layer);
    }

    auto run(const TunBuilderCaptureModel &model, TunBuilderCapture &sut) const -> void override
    {
        RC_ASSERT(sut.tun_builder_set_layer(layer));
        RC_ASSERT(sut.layer.value() == layer);
    }

    auto show(std::ostream &os) const -> void override
    {
        os << "Set Layer to: " << layer;
    }
};

struct SetMTU final : rc::state::Command<TunBuilderCaptureModel, TunBuilderCapture>
{
    int mtu{};

    explicit SetMTU() : mtu(*rc::gen::arbitrary<int>())
    {
    }

    auto apply(TunBuilderCaptureModel &model) const -> void override
    {
        model.mtu = mtu;
    }

    auto run(const TunBuilderCaptureModel &model, TunBuilderCapture &sut) const -> void override
    {
        RC_ASSERT(sut.tun_builder_set_mtu(mtu));
        RC_ASSERT(sut.mtu == mtu);
    }

    auto show(std::ostream &os) const -> void override
    {
        os << "Set mtu to " << mtu;
    }
};

struct SetSessionName final : rc::state::Command<TunBuilderCaptureModel, TunBuilderCapture>
{
    std::string session_name{};

    explicit SetSessionName() : session_name(*rc::gen::arbitrary<std::string>())
    {
    }

    auto apply(TunBuilderCaptureModel &model) const -> void override
    {
        model.session_name = session_name;
    }

    auto run(const TunBuilderCaptureModel &model, TunBuilderCapture &sut) const -> void override
    {
        RC_ASSERT(sut.tun_builder_set_session_name(session_name));
        RC_ASSERT(sut.session_name == session_name);
    }

    auto show(std::ostream &os) const -> void override
    {
        os << "Set session name to " << session_name;
    }
};

struct AddProxyBypass final : rc::state::Command<TunBuilderCaptureModel, TunBuilderCapture>
{
    std::string bypass_host{};

    explicit AddProxyBypass() : bypass_host(*rc::gen::arbitrary<std::string>())
    {
    }

    auto apply(TunBuilderCaptureModel &model) const -> void override
    {
        TunBuilderCapture::ProxyBypass proxy_bypass;
        proxy_bypass.bypass_host = bypass_host;
        model.proxy_bypass.push_back(std::move(proxy_bypass));
    }

    auto run(const TunBuilderCaptureModel &model, TunBuilderCapture &sut) const -> void override
    {
        RC_ASSERT(sut.tun_builder_add_proxy_bypass(bypass_host));
        RC_ASSERT(sut.proxy_bypass.size() == model.proxy_bypass.size() + 1);
        const auto &added_bypass_host = sut.proxy_bypass.back();
        RC_ASSERT(added_bypass_host.bypass_host == bypass_host);
    }

    auto show(std::ostream &os) const -> void override
    {
        os << "Add ProxyBypass: " << bypass_host;
    }
};

struct SetProxyAutoConfigURL final : rc::state::Command<TunBuilderCaptureModel, TunBuilderCapture>
{
    std::string proxy_auto_config_url{};

    explicit SetProxyAutoConfigURL() : proxy_auto_config_url(*rc::gen::arbitrary<std::string>())
    {
    }

    auto apply(TunBuilderCaptureModel &model) const -> void override
    {
        model.proxy_auto_config_url.url = proxy_auto_config_url;
    }

    auto run(const TunBuilderCaptureModel &model, TunBuilderCapture &sut) const -> void override
    {
        RC_ASSERT(sut.tun_builder_set_proxy_auto_config_url(proxy_auto_config_url));
        RC_ASSERT(sut.proxy_auto_config_url.url == proxy_auto_config_url);
    }

    auto show(std::ostream &os) const -> void override
    {
        os << "Set ProxyAutoConfigURL to " << proxy_auto_config_url;
    }
};

struct SetProxyHTTP final : rc::state::Command<TunBuilderCaptureModel, TunBuilderCapture>
{
    std::string host;
    int port{0};

    explicit SetProxyHTTP()
        : host{*rc::gen::arbitrary<std::string>()}, port{*rc::gen::arbitrary<int>()}
    {
    }

    auto apply(TunBuilderCaptureModel &model) const -> void override
    {
        model.http_proxy.host = host;
        model.http_proxy.port = port;
    }

    auto run(const TunBuilderCaptureModel &model, TunBuilderCapture &sut) const -> void override
    {
        RC_ASSERT(sut.tun_builder_set_proxy_http(host, port));
        RC_ASSERT(sut.http_proxy.host == host);
        RC_ASSERT(sut.http_proxy.port == port);
    }

    auto show(std::ostream &os) const -> void override
    {
        os << "Set ProxyHTTP to host: " << host << " port: " << port;
    }
};

struct SetProxyHTTPS final : rc::state::Command<TunBuilderCaptureModel, TunBuilderCapture>
{
    std::string host;
    int port{0};

    explicit SetProxyHTTPS()
        : host{*rc::gen::arbitrary<std::string>()}, port{*rc::gen::arbitrary<int>()}
    {
    }

    auto apply(TunBuilderCaptureModel &model) const -> void override
    {
        model.https_proxy.host = host;
        model.https_proxy.port = port;
    }

    auto run(const TunBuilderCaptureModel &model, TunBuilderCapture &sut) const -> void override
    {
        RC_ASSERT(sut.tun_builder_set_proxy_https(host, port));
        RC_ASSERT(sut.https_proxy.host == host);
        RC_ASSERT(sut.https_proxy.port == port);
    }

    auto show(std::ostream &os) const -> void override
    {
        os << "Set ProxyHTTPS to host: " << host << " port: " << port;
    }
};

struct AddWINSServer final : rc::state::Command<TunBuilderCaptureModel, TunBuilderCapture>
{
    std::string address{};

    explicit AddWINSServer() : address(*rc::gen::arbitrary<std::string>())
    {
    }

    auto apply(TunBuilderCaptureModel &model) const -> void override
    {
        TunBuilderCapture::WINSServer wins;
        wins.address = address;
        model.wins_servers.push_back(std::move(wins));
    }

    auto run(const TunBuilderCaptureModel &model, TunBuilderCapture &sut) const -> void override
    {
        RC_ASSERT(sut.tun_builder_add_wins_server(address));
        RC_ASSERT(sut.wins_servers.size() == model.wins_servers.size() + 1);
        RC_ASSERT(sut.wins_servers.back().address == address);
    }

    auto show(std::ostream &os) const -> void override
    {
        os << "Add WINSServer: " << address;
    }
};

struct SetAllowFamily final : rc::state::Command<TunBuilderCaptureModel, TunBuilderCapture>
{
    int af{};
    bool allow{false};

    explicit SetAllowFamily()
        : af(*rc::gen::elementOf<std::vector<int>>({AF_INET, AF_INET6})),
          allow{*rc::gen::arbitrary<bool>()}
    {
    }

    auto apply(TunBuilderCaptureModel &model) const -> void override
    {
        if (af == AF_INET)
        {
            model.block_ipv4 = !allow;
        }
        else if (af == AF_INET6)
        {
            model.block_ipv6 = !allow;
        }
    }

    auto run(const TunBuilderCaptureModel &model, TunBuilderCapture &sut) const -> void override
    {
        RC_ASSERT(sut.tun_builder_set_allow_family(af, allow));
        if (af == AF_INET)
        {
            RC_ASSERT(sut.block_ipv4 == !allow);
        }
        else if (af == AF_INET6)
        {
            RC_ASSERT(sut.block_ipv6 == !allow);
        }
    }

    auto show(std::ostream &os) const -> void override
    {
        os << "Set allow local family to " << af << allow;
    }
};

struct SetAllowLocalDNS final : rc::state::Command<TunBuilderCaptureModel, TunBuilderCapture>
{
    bool allow{false};

    explicit SetAllowLocalDNS()
        : allow{*rc::gen::arbitrary<bool>()}
    {
    }

    auto apply(TunBuilderCaptureModel &model) const -> void override
    {
        model.block_outside_dns = !allow;
    }

    auto run(const TunBuilderCaptureModel &model, TunBuilderCapture &sut) const -> void override
    {
        RC_ASSERT(sut.tun_builder_set_allow_local_dns(allow));
        RC_ASSERT(sut.block_outside_dns == !allow);
    }

    auto show(std::ostream &os) const -> void override
    {
        os << "Set allow local DNS to " << !allow;
    }
};

struct ResetTunnelAddresses final : rc::state::Command<TunBuilderCaptureModel, TunBuilderCapture>
{
    auto apply(TunBuilderCaptureModel &model) const -> void override
    {
        model.tunnel_addresses.clear();
        model.tunnel_address_index_ipv4 = -1;
        model.tunnel_address_index_ipv6 = -1;
    }

    auto run(const TunBuilderCaptureModel &model, TunBuilderCapture &sut) const -> void override
    {
        sut.reset_tunnel_addresses();
        RC_ASSERT(sut.tunnel_addresses.empty());
        RC_ASSERT(sut.tunnel_address_index_ipv4 == -1);
        RC_ASSERT(sut.tunnel_address_index_ipv6 == -1);
    }

    auto show(std::ostream &os) const -> void override
    {
        os << "Reset Tunnel Addresses";
    }
};

struct ResetDNSOptions final : rc::state::Command<TunBuilderCaptureModel, TunBuilderCapture>
{
    auto apply(TunBuilderCaptureModel &model) const -> void override
    {
        model.dns_options = {};
    }

    auto run(const TunBuilderCaptureModel &model, TunBuilderCapture &sut) const -> void override
    {
        sut.reset_dns_options();
        const DnsOptions dns_options{};
        RC_ASSERT(sut.dns_options == dns_options);
    }

    auto show(std::ostream &os) const -> void override
    {
        os << "Reset DNS Options";
    }
};

struct VPN_IPv4 final : rc::state::Command<TunBuilderCaptureModel, TunBuilderCapture>
{
    auto run(const TunBuilderCaptureModel &model, TunBuilderCapture &sut) const -> void override
    {
        if (model.tunnel_address_index_ipv4 >= 0)
        {
            const auto &ipv4_address = sut.vpn_ipv4();
            RC_ASSERT(model.tunnel_addresses[model.tunnel_address_index_ipv4].address == ipv4_address->address);
            RC_ASSERT(model.tunnel_addresses[model.tunnel_address_index_ipv4].prefix_length == ipv4_address->prefix_length);
            RC_ASSERT(model.tunnel_addresses[model.tunnel_address_index_ipv4].metric == ipv4_address->metric);
            RC_ASSERT(model.tunnel_addresses[model.tunnel_address_index_ipv4].gateway == ipv4_address->gateway);
            RC_ASSERT(model.tunnel_addresses[model.tunnel_address_index_ipv4].ipv6 == ipv4_address->ipv6);
            RC_ASSERT(model.tunnel_addresses[model.tunnel_address_index_ipv4].net30 == ipv4_address->net30);
        }
        else
        {
            RC_ASSERT(sut.vpn_ipv4() == nullptr);
        }
    }

    auto show(std::ostream &os) const -> void override
    {
        os << "VPN IPv4";
    }
};

struct VPN_IPv6 final : rc::state::Command<TunBuilderCaptureModel, TunBuilderCapture>
{
    auto run(const TunBuilderCaptureModel &model, TunBuilderCapture &sut) const -> void override
    {
        if (model.tunnel_address_index_ipv6 >= 0)
        {
            const auto &ipv6_address = sut.vpn_ipv6();
            RC_ASSERT(model.tunnel_addresses[model.tunnel_address_index_ipv6].address == ipv6_address->address);
            RC_ASSERT(model.tunnel_addresses[model.tunnel_address_index_ipv6].prefix_length == ipv6_address->prefix_length);
            RC_ASSERT(model.tunnel_addresses[model.tunnel_address_index_ipv6].metric == ipv6_address->metric);
            RC_ASSERT(model.tunnel_addresses[model.tunnel_address_index_ipv6].gateway == ipv6_address->gateway);
            RC_ASSERT(model.tunnel_addresses[model.tunnel_address_index_ipv6].ipv6 == ipv6_address->ipv6);
            RC_ASSERT(model.tunnel_addresses[model.tunnel_address_index_ipv6].net30 == ipv6_address->net30);
        }
        else
        {
            RC_ASSERT(sut.vpn_ipv6() == nullptr);
        }
    }

    auto show(std::ostream &os) const -> void override
    {
        os << "VPN IPv6";
    }
};

struct VPN_IP final : rc::state::Command<TunBuilderCaptureModel, TunBuilderCapture>
{
    IP::Addr::Version version{};

    explicit VPN_IP() : version{*rc::gen::elementOf<std::vector<IP::Addr::Version>>({IP::Addr::Version::V4, IP::Addr::Version::V6, IP::Addr::Version::UNSPEC})} {};

    auto run(const TunBuilderCaptureModel &model, TunBuilderCapture &sut) const -> void override
    {
        const auto &ip_address = sut.vpn_ip(version);
        if (version == IP::Addr::Version::UNSPEC)
        {
            RC_ASSERT(ip_address == nullptr);
        }
        else if (version == IP::Addr::Version::V4 && model.tunnel_address_index_ipv4 >= 0)
        {
            RC_ASSERT(model.tunnel_addresses[model.tunnel_address_index_ipv4].address == ip_address->address);
            RC_ASSERT(model.tunnel_addresses[model.tunnel_address_index_ipv4].prefix_length == ip_address->prefix_length);
            RC_ASSERT(model.tunnel_addresses[model.tunnel_address_index_ipv4].metric == ip_address->metric);
            RC_ASSERT(model.tunnel_addresses[model.tunnel_address_index_ipv4].gateway == ip_address->gateway);
            RC_ASSERT(model.tunnel_addresses[model.tunnel_address_index_ipv4].ipv6 == ip_address->ipv6);
            RC_ASSERT(model.tunnel_addresses[model.tunnel_address_index_ipv4].net30 == ip_address->net30);
        }
        else if (version == IP::Addr::Version::V6 && model.tunnel_address_index_ipv6 >= 0)
        {
            RC_ASSERT(model.tunnel_addresses[model.tunnel_address_index_ipv6].address == ip_address->address);
            RC_ASSERT(model.tunnel_addresses[model.tunnel_address_index_ipv6].prefix_length == ip_address->prefix_length);
            RC_ASSERT(model.tunnel_addresses[model.tunnel_address_index_ipv6].metric == ip_address->metric);
            RC_ASSERT(model.tunnel_addresses[model.tunnel_address_index_ipv6].gateway == ip_address->gateway);
            RC_ASSERT(model.tunnel_addresses[model.tunnel_address_index_ipv6].ipv6 == ip_address->ipv6);
            RC_ASSERT(model.tunnel_addresses[model.tunnel_address_index_ipv6].net30 == ip_address->net30);
        }
    }

    auto show(std::ostream &os) const -> void override
    {
        os << "VPN IP";
    }
};

RC_GTEST_PROP(TunBuilderCapture, Stateful, ())
{
    const TunBuilderCaptureModel model{};
    TunBuilderCapture sut{};
    check(model, sut, rc::state::gen::execOneOfWithArgs<SetRemoteAddress, AddAddress, RerouteGW, SetRouteMetricDefault, AddRoute, ExcludeRoute, SetLayer, SetMTU, SetSessionName, AddProxyBypass, SetProxyAutoConfigURL, SetProxyHTTP, SetProxyHTTPS, AddWINSServer, SetAllowFamily, SetAllowLocalDNS, ResetTunnelAddresses, ResetDNSOptions, VPN_IPv4, VPN_IPv6, VPN_IP>());
}
