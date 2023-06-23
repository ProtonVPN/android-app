/*
* Copyright (c) 2021 Proton Technologies AG
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
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.vpn.ConnectionParamsWireguard
import com.protonvpn.android.redesign.vpn.ConnectIntent
import io.mockk.mockk
import org.junit.Assert
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WireguardParamsTests {

    @get:Rule var rule = InstantTaskExecutorRule()

    private lateinit var connectionParams: ConnectionParamsWireguard

    @Before
    fun setup() {
        connectionParams =
            ConnectionParamsWireguard(ConnectIntent.Fastest, mockk(), 51820, mockk(), "1.1.1.1", TransmissionProtocol.UDP)
    }

    @Test
    fun testAllowedIpsCalculation() {
        val allowedIps = "0.0.0.0/0, 2000::/3"
        val ipToExclude = "134.209.78.99"
        val allowedIpsWithExclusion = "0.0.0.0/1, 128.0.0.0/6, 132.0.0.0/7, 134.0.0.0/9, 134.128.0.0/10," +
            " 134.192.0.0/12, 134.208.0.0/16, 134.209.0.0/18, 134.209.64.0/21, 134.209.72.0/22," +
            " 134.209.76.0/23, 134.209.78.0/26, 134.209.78.64/27, 134.209.78.96/31, 134.209.78.98/32," +
            " 134.209.78.100/30, 134.209.78.104/29, 134.209.78.112/28, 134.209.78.128/25, 134.209.79.0/24," +
            " 134.209.80.0/20, 134.209.96.0/19, 134.209.128.0/17, 134.210.0.0/15, 134.212.0.0/14," +
            " 134.216.0.0/13, 134.224.0.0/11, 135.0.0.0/8, 136.0.0.0/5, 144.0.0.0/4, 160.0.0.0/3, 192.0.0.0/2, 2000::/3"

        Assert.assertEquals(
            allowedIps,
            connectionParams.calculateAllowedIps(emptyList())
        )
        Assert.assertEquals(
            allowedIpsWithExclusion,
            connectionParams.calculateAllowedIps(listOf(ipToExclude))
        )
        Assert.assertEquals(
            "0.0.0.0/5, 8.0.0.0/7, 10.0.0.0/23, 10.0.3.0/24, 10.0.4.0/22, 10.0.8.0/21, 10.0.16.0/20, " +
                "10.0.32.0/19, 10.0.64.0/18, 10.0.128.0/17, 10.1.0.0/16, 10.2.0.0/15, 10.4.0.0/14, 10.8.0.0/13, " +
                "10.16.0.0/12, 10.32.0.0/11, 10.64.0.0/10, 10.128.0.0/9, 11.0.0.0/8, 12.0.0.0/6, " +
                "16.0.0.0/4, 32.0.0.0/3, 64.0.0.0/2, 128.0.0.0/1, 2000::/3",
            connectionParams.calculateAllowedIps(listOf("10.0.2.16/24"))
        )
    }

    @Test
    fun `loopback addresses are never in allowed IPs`() {
        val allowedIpsString = connectionParams.calculateAllowedIps(listOf("126.0.0.1"))
        val allowedIps = allowedIpsString.split(", ")
        assertFalse(allowedIps.any { it.startsWith("127.") })
    }
}
