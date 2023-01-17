/*
 * Copyright (c) 2019 Proton Technologies AG
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
package com.protonvpn.app

import com.protonvpn.android.BuildConfig
import com.protonvpn.android.utils.NetUtils.maskAnyIP
import com.protonvpn.android.utils.NetUtils.stripIP
import com.protonvpn.android.utils.jitterMs
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Assert
import org.junit.Test
import kotlin.random.Random

class NetUtilsTests {

    @Test
    fun testStripIP() {
        // IPv4
        Assert.assertEquals("12.34.56.0", stripIP("12.34.56.78"))

        // IPv6
        Assert.assertEquals("ABCD:ff00:0000:6789:0:0:0:0",
                stripIP("ABCD:ff00:0000:6789:1234:5678:DCBA:ab12"))

        // invalid
        Assert.assertEquals("abcd", stripIP("abcd"))
    }

    @Test
    fun testMaskAnyIp() {
        val input = "Logs: ${BuildConfig.VERSION_NAME} 51.83.240.0 192.0.2.1 12.34.56.78 ABCD:ff00:0000:6789:1234:5678:DCBA:ab12"
        val expected = "Logs: ${BuildConfig.VERSION_NAME} masked-ipv4 masked-ipv4 masked-ipv4 masked-ipv6"
        Assert.assertEquals(expected, input.maskAnyIP())

        val input2 = "WireGuard port: 443, allowed IPs: 0.0.0.0/3, 51.83.0.0/17, 51.83.240.0/21, 51.83.248.0/22, 51.83.252.0/23, 51.83.254.0/25, 51.83.254.128/26, 51.83.254.192/27, 51.83.254.240/28, 51.83.255.0/"
        val expected2 = "WireGuard port: 443, allowed IPs: masked-ipv4/3, masked-ipv4/17, masked-ipv4/21, masked-ipv4/22, masked-ipv4/23, masked-ipv4/25, masked-ipv4/26, masked-ipv4/27, masked-ipv40/28, masked-ipv4/"
        Assert.assertEquals(expected2, input2.maskAnyIP())
    }

    @Test
    fun jitterTest() {
        val random = mockk<Random>()
        val rangeSlot = slot<Long>()
        every { random.nextLong(capture(rangeSlot)) } answers { rangeSlot.captured / 2 }
        Assert.assertEquals(1100, jitterMs(1000, .2f, 1000, random))
        Assert.assertEquals(1005, jitterMs(1000, .2f, 10, random))
    }
}
