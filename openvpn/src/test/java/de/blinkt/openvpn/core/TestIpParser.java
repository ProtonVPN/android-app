/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import org.junit.Assert;
import org.junit.Test;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Created by arne on 23.07.16.
 */

public class TestIpParser {

    @Test
    public void parseIPv6Zeros() throws UnknownHostException {

        testAddress("2020:0:1234::", 45, "2020:0:1234::/45");
        testAddress("::", 0, "::/0");
        testAddress("2a02:2e0:3fe:1001:302::", 128, "2a02:2e0:3fe:1001:302::/128");
        testAddress("2a02:2e0:3fe:1001:302::70", 128, "2a02:2e0:3fe:1001:302:0:0:70/128");
    }

    void testAddress(String input, int mask, String output) throws UnknownHostException {
        Inet6Address ip = (Inet6Address) InetAddress.getByName(input);

        NetworkSpace.IpAddress netIp = new NetworkSpace.IpAddress(ip, mask, true);

        Assert.assertEquals(output, netIp.toString());
    }
}
