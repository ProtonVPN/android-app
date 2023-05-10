#include "test_common.h"
#include <iostream>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/string.hpp>
#include <openvpn/common/options.hpp>
#include <openvpn/server/listenlist.hpp>

using namespace openvpn;

static std::string expected = "0 [errors-to-stderr]\n"
                              "1 [log] [/Library/Application Support/OpenVPN/log/ovpn3_yonan_net_p0977.log]\n"
                              "2 [config] [stdin]\n"
                              "3 [proto-force] [udp]\n"
                              "4 [management] [/Library/Application Support/OpenVPN/sock/ovpn-6QSai9SzvRcm.sock] [unix]\n"
                              "5 [setenv] [UV_ASCLI_VER] [2.0.18.200]\n"
                              "6 [setenv] [UV_PLAT_REL] [12.5.0]\n"
                              "7 [auth-nocache]\n"
                              "8 [management-hold]\n"
                              "9 [management-client]\n"
                              "10 [management-query-passwords]\n"
                              "11 [management-query-remote]\n"
                              "12 [management-up-down]\n"
                              "13 [management-client-user] [root]\n"
                              "14 [allow-pull-fqdn]\n"
                              "15 [auth-retry] [interact]\n"
                              "16 [push-peer-info]\n"
                              "17 [setenv] [UV_ASCLI_VER] [2.0.18.200]\n"
                              "18 [setenv] [UV_PLAT_REL] [12.5.0]\n";

static const char *input[] = {
    // clang-format off
    "unittest",
    "--errors-to-stderr",
    "--log", "/Library/Application Support/OpenVPN/log/ovpn3_yonan_net_p0977.log",
    "--config", "stdin",
    "--proto-force", "udp",
    "--management", "/Library/Application Support/OpenVPN/sock/ovpn-6QSai9SzvRcm.sock",
    "unix",
    "--setenv", "UV_ASCLI_VER", "2.0.18.200",
    "--setenv", "UV_PLAT_REL", "12.5.0",
    "--auth-nocache",
    "--management-hold",
    "--management-client",
    "--management-query-passwords",
    "--management-query-remote",
    "--management-up-down",
    "--management-client-user", "root",
    "--allow-pull-fqdn",
    "--auth-retry", "interact",
    "--push-peer-info",
    "--setenv", "UV_ASCLI_VER", "2.0.18.200",
    "--setenv", "UV_PLAT_REL", "12.5.0"
    // clang-format on
};

TEST(argv, parse)
{
    const OptionList opt = OptionList::parse_from_argv_static(string::from_argv(sizeof(input) / sizeof(char *),
                                                                                const_cast<char **>(input),
                                                                                true));
    ASSERT_EQ(expected, opt.render(Option::RENDER_NUMBER | Option::RENDER_BRACKET));
}

static const char config[] = "listen 1.2.3.4 1000 tcp 2\n"
                             "listen 0.0.0.0 4000 tcp 4*N\n"
                             "listen ::0 8000 tcp\n"
                             "listen sock/ststrack-%s.sock unix-stream\n";

TEST(argv, portoffset1)
{
    const OptionList opt1 = OptionList::parse_from_config_static(config, nullptr);
    const Listen::List ll1(opt1, "listen", Listen::List::Nominal, 4);

    EXPECT_EQ(
        "listen 1.2.3.4 1000 TCPv4 2\nlisten 0.0.0.0 4000 TCPv4 16\nlisten ::0 8000 TCPv6 1\nlisten sock/ststrack-%s.sock UnixStream 1\n",
        ll1.to_string());

    std::string exp2("listen 1.2.3.4 1000 TCPv4 0\nlisten 1.2.3.4 1001 TCPv4 0\n");

    for (int i = 4000; i < 4016; i++)
        exp2 += "listen 0.0.0.0 " + std::to_string(i) + " TCPv4 0\n";

    exp2 += "listen ::0 8000 TCPv6 0\n"
            "listen sock/ststrack-0.sock UnixStream 0\n";

    const Listen::List ll2 = ll1.expand_ports_by_n_threads(100);
    EXPECT_EQ(exp2, ll2.to_string());
}

TEST(argv, portoffset2)
{
    const OptionList opt = OptionList::parse_from_config_static(config, nullptr);
    const Listen::List ll(opt, "listen", Listen::List::Nominal, 4);
    for (unsigned int unit = 0; unit < 4; ++unit)
    {
        std::stringstream exp;
        exp << "listen 1.2.3.4 " << 1000 + unit << " TCPv4 0\n";
        ;
        exp << "listen 0.0.0.0 400" << unit << " TCPv4 0\n";
        exp << "listen ::0 800" << unit << " TCPv6 0\n";
        exp << "listen sock/ststrack-" << unit << ".sock UnixStream 0\n";

        const Listen::List llu = ll.expand_ports_by_unit(unit);
        EXPECT_EQ(exp.str(), llu.to_string());
    }
}

static void extract_auth_token(const OptionList &opt)
{
    const Option &o = opt.get("auth-token");
    o.min_args(2);
    ASSERT_EQ("auth-token SESS_ID_wJdhHMc7tr9GwbMNEW6b+A==", o.render(0));
}

static void verify_topology(const OptionList &opt)
{
    const Option &o = opt.get("topology");
    o.min_args(2);
    if (o.ref(1) != "subnet")
        throw option_error("only topology subnet supported");
}

static void extract_ifconfig(const OptionList &opt)
{
    const Option &o = opt.get("ifconfig");
    o.exact_args(3);
    std::string ip = IP::Addr::validate(o.ref(1), "ifconfig-ip");
    std::string mask = IP::Addr::validate(o.ref(2), "ifconfig-net");
    ASSERT_EQ("5.5.8.4", ip);
    ASSERT_EQ("255.255.252.0", mask);
}

TEST(argv, parsetest)
{
    const std::string opt_csv = "explicit-exit-notify,topology subnet,route-delay 5 30,dhcp-pre-release,dhcp-renew,dhcp-release,route-metric 101,ping 5,ping-restart 40,redirect-gateway def1,redirect-gateway bypass-dhcp,redirect-gateway autolocal,route-gateway 5.5.8.1,dhcp-option DNS 172.16.0.23,register-dns,auth-token SESS_ID_wJdhHMc7tr9GwbMNEW6b+A==,comp-lzo no,ifconfig 5.5.8.4 255.255.252.0";
    OptionList opt;
    OptionList::Limits limits("parsetest limit out of range", 2048, 16, 8, 512, 64);
    opt.parse_from_csv(opt_csv, &limits);
    opt.update_map();
    ASSERT_EQ("explicit-exit-notify\n"
              "topology subnet\n"
              "route-delay 5 30\n"
              "dhcp-pre-release\n"
              "dhcp-renew\n"
              "dhcp-release\n"
              "route-metric 101\n"
              "ping 5\n"
              "ping-restart 40\n"
              "redirect-gateway def1\n"
              "redirect-gateway bypass-dhcp\n"
              "redirect-gateway autolocal\n"
              "route-gateway 5.5.8.1\n"
              "dhcp-option DNS 172.16.0.23\n"
              "register-dns\n"
              "auth-token SESS_ID_wJdhHMc7tr9GwbMNEW6b+A==\n"
              "comp-lzo no\n"
              "ifconfig 5.5.8.4 255.255.252.0\n",
              opt.render(0));
    extract_auth_token(opt);
    extract_ifconfig(opt);
    verify_topology(opt);
    ASSERT_EQ(1066u, limits.get_bytes());
}

static void csv_test(const std::string &str)
{
    std::vector<std::string> list = Split::by_char<std::vector<std::string>, StandardLex, Split::NullLimit>(str, ',');
    std::stringstream s;
    std::copy(list.begin(), list.end(), std::ostream_iterator<std::string>(s, "\n"));
    ASSERT_EQ("this\n"
              "is\n"
              "\n"
              "a\n"
              "\"foo,bar\"\n"
              "test\n",
              s.str());
}

static void space_test(const std::string &str, const std::string &expected)
{
    std::vector<std::string> list = Split::by_space<std::vector<std::string>, StandardLex, SpaceMatch, Split::NullLimit>(str);
    std::stringstream s;
    std::copy(list.begin(), list.end(), std::ostream_iterator<std::string>(s, "\n"));

    ASSERT_EQ(expected, s.str());
}

static void options_csv_test(const std::string &str, const std::string &elem)
{
    const OptionList olist = OptionList::parse_from_csv_static(str, NULL);
    ASSERT_EQ(getSortedString("V4\n"
                              "dev-type tun\n"
                              "link-mtu 1558\n"
                              "tun-mtu 1500\n"
                              "proto UDPv4\n"
                              "comp-lzo\n"
                              "keydir 1\n"
                              "cipher AES-256-CBC\n"
                              "auth SHA1\n"
                              "keysize 256\n"
                              "tls-auth\n"
                              "key-method 2\n"
                              "tls-client\n"),
              getSortedString(olist.render(0)));

    ASSERT_EQ(getSortedString("tls-client [ 12 ]\n"
                              "key-method [ 11 ]\n"
                              "tls-auth [ 10 ]\n"
                              "link-mtu [ 2 ]\n"
                              "auth [ 8 ]\n"
                              "V4 [ 0 ]\n"
                              "comp-lzo [ 5 ]\n"
                              "tun-mtu [ 3 ]\n"
                              "proto [ 4 ]\n"
                              "keysize [ 9 ]\n"
                              "keydir [ 6 ]\n"
                              "dev-type [ 1 ]\n"
                              "cipher [ 7 ]\n"),
              getSortedString(olist.render_map()));

    if (!elem.empty())
    {
        OptionList::IndexMap::const_iterator e = olist.map().find(elem);
        ASSERT_TRUE(e != olist.map().end());
    }
}

TEST(argv, parsetest1)
{
    csv_test("this,is,,a,\"foo,bar\",test");
    space_test(R"(  this is a "foo \\ bar" test   of something \"rather\" grt  )", "this\nis\na\nfoo \\ bar\ntest\nof\nsomething\n\"rather\"\ngrt\n");
    space_test(R"(this is a te""st a "" b)", "this\nis\na\ntest\na\n\nb\n");
    options_csv_test("V4,dev-type tun,link-mtu 1558,tun-mtu 1500,proto UDPv4,comp-lzo,keydir 1,cipher AES-256-CBC,auth SHA1,keysize 256,tls-auth,key-method 2,tls-client",
                     "");
}
