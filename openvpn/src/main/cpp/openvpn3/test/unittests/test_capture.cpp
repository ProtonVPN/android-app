#include "test_common.h"

#include <iostream>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/file.hpp>
#include <openvpn/tun/builder/capture.hpp>

using namespace openvpn;

TEST(misc, capture)
{
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
    tbc->tun_builder_add_dns_server("8.8.8.8", false);
    tbc->tun_builder_add_dns_server("8.8.4.4", false);
    tbc->tun_builder_add_search_domain("yonan.net");
    tbc->tun_builder_add_search_domain("openvpn.net");
    tbc->tun_builder_add_search_domain("privatetunnel.com");
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

    const std::string fn2 = "cap2.txt";
    TunBuilderCapture::Ptr tbc2 = TunBuilderCapture::from_json(j1);
    tbc2->validate();
    Json::Value j2 = tbc2->to_json();
    const std::string j2_txt = j2.toStyledString();
    // OPENVPN_LOG("writing to " << fn2);
    // write_string(fn2, j2_txt);
    // OPENVPN_LOG("JSON #2:\n" << j2_txt);

    ASSERT_EQ(j1_txt, j2_txt) << "round trip failed";
}
