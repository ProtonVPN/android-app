/*
 * Copyright (c) 2025. Proton AG
 *
 * This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.protonvpn.app.vpn

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.protonvpn.android.models.vpn.usecase.ComputeAllowedIPs
import com.protonvpn.android.models.vpn.usecase.FULL_RANGE_IP_V4
import com.protonvpn.android.models.vpn.usecase.FULL_RANGE_IP_V6
import com.protonvpn.android.models.vpn.usecase.ProvideLocalNetworks
import com.protonvpn.android.models.vpn.usecase.joinToIPList
import com.protonvpn.android.models.vpn.usecase.removeIPsFromRanges
import com.protonvpn.android.models.vpn.usecase.toIPAddress
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.settings.data.SplitTunnelingSettings
import inet.ipaddr.IPAddress
import inet.ipaddr.IPAddressString
import inet.ipaddr.ipv4.IPv4Address
import inet.ipaddr.ipv4.IPv4AddressSeqRange
import inet.ipaddr.ipv6.IPv6Address
import inet.ipaddr.ipv6.IPv6AddressSeqRange
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ComputeAllowedIPsTests {

    @get:Rule var rule = InstantTaskExecutorRule()

    private lateinit var computeAllowedIPs: ComputeAllowedIPs

    private lateinit var localNetworks: List<IPAddress>

    @Before
    fun setup() {
        localNetworks = emptyList()
        computeAllowedIPs = ComputeAllowedIPs(ProvideLocalNetworks { _, _ -> localNetworks })
    }

    @Test
    fun testRemoveIpFromRanges() {
        fun ips(vararg ips: String) = ips.map { IPAddressString(it).address }

        fun v4Range(start: String, end: String) = IPv4AddressSeqRange(
            IPAddressString(start).address as IPv4Address,
            IPAddressString(end).address as IPv4Address
        )
        assertEquals(
            listOf(
                v4Range("0.0.0.0", "1.1.1.0"),
                v4Range("1.1.1.2", "2.255.255.255"),
                v4Range("4.0.0.0", "255.255.255.255")
            ),
            removeIPsFromRanges(
                listOf(FULL_RANGE_IP_V4),
                ips("1.1.1.1", "3.0.0.0/8")
            )
        )

        fun v6Range(start: String, end: String) = IPv6AddressSeqRange(
            IPAddressString(start).address as IPv6Address,
            IPAddressString(end).address as IPv6Address
        )
        assertEquals(
            listOf(
                v6Range("::", "0:ffff:ffff:ffff:ffff:ffff:ffff:ffff"),
                v6Range("1::1", "2:ffff:ffff:ffff:ffff:ffff:ffff:ffff"),
                v6Range("0003:0000:0000:0001::", "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff")
            ),
            removeIPsFromRanges(
                listOf(FULL_RANGE_IP_V6),
                ips("1::", "3::/64")
            )
        )
    }

    @Test
    fun testExcludeFrom() {
        val defaultAllowedIps = "0.0.0.0/0"
        val ipToExclude = "134.209.78.99"
        val allowedIpsWithExclusion = "0.0.0.0/1, 128.0.0.0/6, 132.0.0.0/7, 134.0.0.0/9, 134.128.0.0/10," +
            " 134.192.0.0/12, 134.208.0.0/16, 134.209.0.0/18, 134.209.64.0/21, 134.209.72.0/22," +
            " 134.209.76.0/23, 134.209.78.0/26, 134.209.78.64/27, 134.209.78.96/31, 134.209.78.98/32," +
            " 134.209.78.100/30, 134.209.78.104/29, 134.209.78.112/28, 134.209.78.128/25, 134.209.79.0/24," +
            " 134.209.80.0/20, 134.209.96.0/19, 134.209.128.0/17, 134.210.0.0/15, 134.212.0.0/14," +
            " 134.216.0.0/13, 134.224.0.0/11, 135.0.0.0/8, 136.0.0.0/5, 144.0.0.0/4, 160.0.0.0/3, 192.0.0.0/2"

        assertEquals(
            defaultAllowedIps,
            computeAllowedIPs.excludeFrom(FULL_RANGE_IP_V4, emptyList())
                .joinToIPList()
                .joinToString { it.toCanonicalString() }
        )
        assertEquals(
            allowedIpsWithExclusion,
            computeAllowedIPs.excludeFrom(
                FULL_RANGE_IP_V4,
                listOf(ipToExclude.toIPAddress())
            ).joinToIPList().joinToString { it.toCanonicalString() }
        )
        assertEquals(
            "0.0.0.0/5, 8.0.0.0/7, 10.0.0.0/23, 10.0.3.0/24, 10.0.4.0/22, 10.0.8.0/21, 10.0.16.0/20, " +
                "10.0.32.0/19, 10.0.64.0/18, 10.0.128.0/17, 10.1.0.0/16, 10.2.0.0/15, 10.4.0.0/14, 10.8.0.0/13, " +
                "10.16.0.0/12, 10.32.0.0/11, 10.64.0.0/10, 10.128.0.0/9, 11.0.0.0/8, 12.0.0.0/6, " +
                "16.0.0.0/4, 32.0.0.0/3, 64.0.0.0/2, 128.0.0.0/1",
            computeAllowedIPs.excludeFrom(
                FULL_RANGE_IP_V4,
                listOf("10.0.2.16/24".toIPAddress())
            ).joinToIPList().joinToString { it.toCanonicalString() }
        )
    }

    @Test
    fun `loopback addresses are never in allowed IPs`() {
        val allowedIps4 = computeAllowedIPs
            .excludeFrom(FULL_RANGE_IP_V4, listOf("126.0.0.1".toIPAddress()))
            .joinToIPList()
        assertFalse(allowedIps4.any { it.toCanonicalString().startsWith("127.") })

        val allowedIps6 = computeAllowedIPs
            .excludeFrom(FULL_RANGE_IP_V6, listOf("::".toIPAddress()))
            .joinToIPList()
        assertFalse(allowedIps6.any { it.isLoopback })
    }

    @Test
    fun `v6 setting off always includes full v6 range`() {
        assertEquals(setOf("::/0", "0.0.0.0/0"), computeStringSet(supportIPv6 = false))

        // LAN
        localNetworks = listOf("fe80::/10".toIPAddress())
        assertEquals(setOf("::/0", "0.0.0.0/0"), computeStringSet(supportIPv6 = false, lanConnections = true))

        // Split tunneling include
        assertEquals(
            setOf("::/0", "1.1.1.1/32"),
            computeStringSet(supportIPv6 = false, splitTunneling = splitInclude("1.1.1.1", "::2"))
        )

        // Split tunneling exclude
        assertEquals(
            setOf("::/0", "0.0.0.0/0"),
            computeStringSet(supportIPv6 = false, splitTunneling = splitExclude("::2"))
        )

        // Always included IPv6s ignored when v6 is disabled
        assertEquals(
            setOf("::/0", "1.1.1.1/32"),
            computeStringSet(
                supportIPv6 = false,
                splitTunneling = splitInclude("1.1.1.1", "::3"),
                alwaysIncludeIPs = listOf("::2".toIPAddress())
            )
        )
    }

    @Test
    fun `always included IPs are included`() {
        val result = computeStringSet(
            splitTunneling = splitInclude("2.2.2.2", "2::1"),
            alwaysIncludeIPs = listOf("1.1.1.1".toIPAddress(), "::2".toIPAddress())
        )
        assertEquals(setOf("1.1.1.1/32", "::2/128", "2.2.2.2/32", "2::1/128"), result)
    }

    @Test
    fun `overlapping ranges are merged`() {
        assertEquals(setOf("1.1.1.0/31"), computeStringSet(splitTunneling = splitInclude("1.1.1.0", "1.1.1.1")))
        assertEquals(setOf("2000::/3"), computeStringSet(splitTunneling = splitInclude("2000::/4", "3000::/4")))
    }

    @Test
    fun `LAN ranges are excluded`() {
        localNetworks = listOf("fe80::/10", "1.0.0.0/2").map { it.toIPAddress() }
        assertEquals(
            setOf("64.0.0.0/2", "128.0.0.0/1", "::/1", "8000::/2", "c000::/3", "e000::/4",
                "f000::/5", "f800::/6", "fc00::/7", "fe00::/9", "fec0::/10", "ff00::/8"),
            computeStringSet(lanConnections = true)
        )
        assertEquals(setOf("0.0.0.0/0", "::/0"), computeStringSet(lanConnections = false))
    }

    private fun splitInclude(vararg ips: String) = SplitTunnelingSettings(
        isEnabled = true,
        mode = SplitTunnelingMode.INCLUDE_ONLY,
        includedIps = ips.toList()
    )

    private fun splitExclude(vararg ips: String) = SplitTunnelingSettings(
        isEnabled = true,
        mode = SplitTunnelingMode.EXCLUDE_ONLY,
        excludedIps = ips.toList()
    )

    private fun computeStringSet(
        supportIPv6: Boolean = true,
        lanConnections: Boolean = false,
        splitTunneling: SplitTunnelingSettings = SplitTunnelingSettings(),
        alwaysIncludeIPs: List<IPAddress> = emptyList()
    ) = computeAllowedIPs(
        LocalUserSettings(ipV6Enabled = supportIPv6, splitTunneling = splitTunneling, lanConnections = lanConnections),
        alwaysIncludeIPs
    ).map { it.toCanonicalString() }.toSet()
}