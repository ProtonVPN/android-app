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
#include "test_common.h"

#include <openvpn/common/argv.hpp>
#include <openvpn/win/call.hpp>
#include <openvpn/tun/win/tunutil.hpp>

using namespace openvpn;
using namespace openvpn::TunWin::Util;

namespace unittests
{
    static std::string path_to_ip;

    class IpHelperTest : public testing::Test
    {
    protected:
	void SetUp() override
	{
	    // get index of default network adapter
	    DWORD addr;
	    inet_pton(AF_INET, "8.8.8.8", &addr);
	    ::GetBestInterface(addr, &tap.index);
	}

	virtual void TearDown()
	{
	    remove_cmds.execute(os);
	    remove_cmds.clear();
	}

	TapNameGuidPair tap;
	ActionList remove_cmds;
	std::ostringstream os;
    };

    TEST_F(IpHelperTest, TestAddRoute4)
    {
	const char* gw = "10.10.123.123";
	const char* route = "10.10.0.0";
	int route_prefix = 16;
	int metric = 123;

	TunIPHELPER::AddRoute4Cmd cmd{route, route_prefix, tap, gw, metric, true};
	remove_cmds.add(new TunIPHELPER::AddRoute4Cmd{route, route_prefix, tap, gw, metric, false});

	// add route
	cmd.execute(os);

	// get next hop for routed address
	MIB_IPFORWARDROW row;
	DWORD addr;
	inet_pton(AF_INET, "10.10.0.3", &addr);
	GetBestRoute(addr, 0, &row);
	char next_hop[INET_ADDRSTRLEN];
	inet_ntop(AF_INET, &row.dwForwardNextHop, next_hop, INET_ADDRSTRLEN);

	// the next hop should equal route gw 
	ASSERT_STREQ(gw, next_hop);
    }
}
