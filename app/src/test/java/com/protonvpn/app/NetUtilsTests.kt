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
    fun jitterTest() {
        val random = mockk<Random>()
        val rangeSlot = slot<Long>()
        every { random.nextLong(capture(rangeSlot)) } answers { rangeSlot.captured / 2 }
        Assert.assertEquals(1100, jitterMs(1000, .2f, 1000, random))
        Assert.assertEquals(1005, jitterMs(1000, .2f, 10, random))
    }
}
