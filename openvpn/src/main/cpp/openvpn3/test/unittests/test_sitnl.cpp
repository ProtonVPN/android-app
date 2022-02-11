//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2020 OpenVPN Inc.
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

#include <fstream>
#include <sys/capability.h>
#include "test_common.h"

#include "openvpn/common/argv.hpp"
#include "openvpn/common/process.hpp"
#include "openvpn/common/redir.hpp"
#include "openvpn/common/splitlines.hpp"

#include "openvpn/tun/linux/client/sitnl.hpp"

using namespace openvpn;
using namespace TunNetlink;

namespace unittests
{
    static std::string path_to_ip;

    class SitnlTest : public testing::Test
    {
    private:
      void add_device(std::string name)
      {
	RedirectPipe::InOut pipe;
	Argv argv;
	argv.emplace_back(path_to_ip);
	argv.emplace_back("tuntap");
	argv.emplace_back("add");
	argv.emplace_back("mode");
	argv.emplace_back("tun");
	argv.emplace_back(std::move(name));
	system_cmd(argv[0], argv, nullptr, pipe, 0, nullptr);
      }

      void remove_device(std::string name)
      {
	RedirectPipe::InOut pipe;
	Argv argv;
	argv.emplace_back(path_to_ip);
	argv.emplace_back("tuntap");
	argv.emplace_back("delete");
	argv.emplace_back("mode");
	argv.emplace_back("tun");
	argv.emplace_back(std::move(name));
	system_cmd(argv[0], argv, nullptr, pipe, 0, nullptr);
      }

    protected:
      static void SetUpTestSuite()
      {
	// different distros have ip tool in different places
	std::vector<std::string> paths{"/bin/ip", "/sbin/ip", "/usr/bin/ip", "/usr/sbin/ip"};
	for (const auto& path : paths)
	{
	  std::ifstream f(path);
	  if (f)
	  {
	    path_to_ip = path;
	    break;
	  }
	}
	ASSERT_FALSE(path_to_ip.empty()) << "unable to find ip tool";
      }

      static bool haveCapNetAdmin()
      {
        cap_t cap = cap_get_proc();
        cap_flag_value_t v = CAP_CLEAR;
        cap_get_flag(cap, CAP_NET_ADMIN, CAP_EFFECTIVE, &v);
        cap_free(cap);
        return v == CAP_SET;
      }

      void SetUp() override
      {
	if (!haveCapNetAdmin())
	  GTEST_SKIP() << "Need CAP_NET_ADMIN to run this test";

	add_device(dev);
	add_device(dev2);
      }

      void TearDown() override
      {
	remove_device(dev);
	remove_device(dev2);
      }

      template <typename CALLBACK>
      void cmd(Argv argv, CALLBACK cb)
      {
	// runs command, reads output and calls a callback
	RedirectPipe::InOut pipe;
	ASSERT_EQ(system_cmd(argv[0], argv, nullptr, pipe, 0, nullptr), 0) << "failed to run command " << argv[0];

	SplitLines sl(pipe.out);
	bool called = false;
	while (sl()) {
	  const std::string &line = sl.line_ref();

	  std::vector<std::string> v = Split::by_space<std::vector<std::string>, NullLex, SpaceMatch, Split::NullLimit>(line);

	  // blank line?
	  if (v.empty())
	    continue;

	  cb(v, pipe.out, called);
	}

	ASSERT_TRUE(called) << pipe.out;
      }

      template <typename CALLBACK>
      void ip_a_show_dev(CALLBACK cb)
      {
	// get addrs with "ip a show dev"
	RedirectPipe::InOut pipe;
	Argv argv;
	argv.emplace_back(path_to_ip);
	argv.emplace_back("a");
	argv.emplace_back("show");
	argv.emplace_back("dev");
	argv.emplace_back(dev);
	cmd(argv, cb);
      }

      template <typename CALLBACK>
      void ip_route_get(std::string dst, CALLBACK cb)
      {
	// get route with "ip route get"
	RedirectPipe::InOut pipe;
	Argv argv;
	argv.emplace_back(path_to_ip);
	argv.emplace_back("route");
	argv.emplace_back("get");
	argv.emplace_back(std::move(dst));
	cmd(argv, cb);
      }

      std::string dev = "tun999";
      std::string dev2 = "tun9999";

      std::string addr4 = "10.10.0.2";
      std::string route4 = "10.110.0.0/24";
      std::string gw4 = "10.10.0.1";

      std::string addr6 = "fe80:20c3:aaaa:bbbb::cccc";
      std::string route6 = "fe80:20c3:cccc:dddd::0/64";
      std::string gw6 = "fe80:20c3:aaaa:bbbb:cccc:dddd:eeee:1";

      int ipv4_prefix_len = 16;
      int ipv6_prefix_len = 64;
      int mtu = 1234;
    };

    TEST_F(SitnlTest, TestAddrAdd4)
    {
      auto broadcast = IPv4::Addr::from_string(addr4) | ~IPv4::Addr::netmask_from_prefix_len(ipv4_prefix_len);
      ASSERT_EQ(SITNL::net_addr_add(dev, IPv4::Addr::from_string(addr4), ipv4_prefix_len, broadcast), 0);

      ip_a_show_dev([this, &broadcast](std::vector<std::string>& v, const std::string& out, bool& called) {
	if (v[0] == "inet")
	{
	  called = true;
	  ASSERT_EQ(v[1], addr4 + "/" + std::to_string(ipv4_prefix_len)) << out;
	  ASSERT_EQ(v[3], broadcast.to_string()) << out;
	}
      });
    }

    TEST_F(SitnlTest, TestAddrAdd6)
    {
      ASSERT_EQ(SITNL::net_addr_add(dev, IPv6::Addr::from_string(addr6), ipv6_prefix_len), 0);

      ip_a_show_dev([this](std::vector<std::string>& v, const std::string& out, bool& called) {
	if (v[0] == "inet6")
	{
	  called = true;
	  ASSERT_EQ(v[1], addr6 + "/" + std::to_string(ipv6_prefix_len)) << out;
	}
      });
    }

    TEST_F(SitnlTest, TestSetMTU)
    {
      ASSERT_EQ(SITNL::net_iface_mtu_set(dev, mtu), 0);

      ip_a_show_dev([this](std::vector<std::string>& v, const std::string& out, bool& called) {
	if ((v.size() > 1) && (v[1] == dev + ":"))
	{
	  called = true;
	  ASSERT_EQ(v[4], std::to_string(mtu)) << out;
	}
      });
    }

    TEST_F(SitnlTest, TestAddRoute4)
    {
      // add address
      auto broadcast = IPv4::Addr::from_string(addr4) | ~IPv4::Addr::netmask_from_prefix_len(ipv4_prefix_len);
      ASSERT_EQ(SITNL::net_addr_add(dev, IPv4::Addr::from_string(addr4), ipv4_prefix_len, broadcast), 0);

      // up interface
      ASSERT_EQ(SITNL::net_iface_up(dev, true), 0);

      // add route
      ASSERT_EQ(SITNL::net_route_add(IP::Route4(route4), IPv4::Addr::from_string(gw4), dev, 0, 0), 0);

      std::string dst{"10.110.0.100"};

      ip_route_get(dst, [this, &dst](std::vector<std::string>& v, const std::string& out, bool& called) {
	if (v[0] == dst)
	{
	  called = true;
	  v.resize(7);
	  auto expected = std::vector<std::string>{dst, "via", gw4, "dev", dev, "src", addr4};
	  ASSERT_EQ(v, expected) << out;
	}
      });
    }

    TEST_F(SitnlTest, TestAddRoute6)
    {
      // add address
      ASSERT_EQ(SITNL::net_addr_add(dev, IPv6::Addr::from_string(addr6), ipv6_prefix_len), 0);

      // up interface
      ASSERT_EQ(SITNL::net_iface_up(dev, true), 0);

      // add route
      ASSERT_EQ(SITNL::net_route_add(IP::Route6(route6), IPv6::Addr::from_string(gw6), dev, 0, 0), 0);

      std::string dst{"fe80:20c3:cccc:dddd:cccc:dddd:eeee:ffff"};

      ip_route_get(dst, [this, &dst](std::vector<std::string> &v1, const std::string &out, bool &called) {
	  if (v1[0] == dst)
	  {
	    called = true;
	    v1.resize(7);
	    // iptools 4.15 (Ubuntu 18)
	    auto expected1 = std::vector<std::string>{dst, "from", "::", "via", gw6, "dev", dev};
	    auto ok1 = (v1 == expected1);

	    auto v2 = v1;
	    v2.resize(5);
	    // iptools 4.11 (CentOS 7)
	    auto expected2 = std::vector<std::string>{dst, "via", gw6, "dev", dev};
	    auto ok2 = (v2 == expected2);

	    if (!ok1 && !ok2)
	    {
	      // this is just a way to print actual value and all expected values
	      EXPECT_EQ(v1, expected1);
	      EXPECT_EQ(v2, expected2);
	    }
	  }
      });
    }

    TEST_F(SitnlTest, TestBestGw4)
    {
      // add address
      auto broadcast = IPv4::Addr::from_string(addr4) | ~IPv4::Addr::netmask_from_prefix_len(ipv4_prefix_len);
      ASSERT_EQ(SITNL::net_addr_add(dev, IPv4::Addr::from_string(addr4), ipv4_prefix_len, broadcast), 0);

      // up interface
      ASSERT_EQ(SITNL::net_iface_up(dev, true), 0);

      // add routes

      // shortest prefix
      ASSERT_EQ(SITNL::net_route_add(IP::Route4("10.0.0.0/8"), IPv4::Addr::from_string("10.10.10.10"), dev, 0, 0), 0);
      // longest prefix, lowest metric
      ASSERT_EQ(SITNL::net_route_add(IP::Route4("10.10.10.0/24"), IPv4::Addr::from_string("10.10.10.13"), dev, 0, 0), 0);
      // short prefix
      ASSERT_EQ(SITNL::net_route_add(IP::Route4("10.10.0.0/16"), IPv4::Addr::from_string("10.10.10.11"), dev, 0, 0), 0);
      // longest prefix, highest metric
      ASSERT_EQ(SITNL::net_route_add(IP::Route4("10.10.10.0/24"), IPv4::Addr::from_string("10.10.10.12"), dev, 0, 10), 0);

      IPv4::Addr best_gw;
      std::string best_iface;
      ASSERT_EQ(SITNL::net_route_best_gw(IP::Route4("10.10.10.1/32"), best_gw, best_iface), 0);

      // we should get a gateway with longest prefix and lowest metric

      ASSERT_EQ(best_gw.to_string(), "10.10.10.13");
      ASSERT_EQ(best_iface, dev);
    }

    TEST_F(SitnlTest, TestBestGw4FilterIface)
    {
      // add addresses
      auto broadcast = IPv4::Addr::from_string(addr4) | ~IPv4::Addr::netmask_from_prefix_len(ipv4_prefix_len);
      ASSERT_EQ(SITNL::net_addr_add(dev, IPv4::Addr::from_string(addr4), ipv4_prefix_len, broadcast), 0);

      broadcast = IPv4::Addr::from_string("10.20.0.2") | ~IPv4::Addr::netmask_from_prefix_len(ipv4_prefix_len);
      ASSERT_EQ(SITNL::net_addr_add(dev2, IPv4::Addr::from_string("10.20.0.2"), ipv4_prefix_len, broadcast), 0);

      // up interfaces
      SITNL::net_iface_up(dev, true);
      SITNL::net_iface_up(dev2, true);

      // add routes
      ASSERT_EQ(SITNL::net_route_add(IP::Route4("10.11.0.0/16"), IPv4::Addr::from_string("10.10.0.1"), dev, 0, 0), 0);
      ASSERT_EQ(SITNL::net_route_add(IP::Route4("10.11.12.0/24"), IPv4::Addr::from_string("10.20.0.1"), dev2, 0, 0), 0);

      IPv4::Addr best_gw;
      std::string best_iface;

      // filter out gateway with longest prefix route
      SITNL::net_route_best_gw(IP::Route4("10.11.12.13/32"), best_gw, best_iface, dev2);

      ASSERT_EQ(best_gw.to_string(), "10.10.0.1");
      ASSERT_EQ(best_iface, dev);
    }
}
