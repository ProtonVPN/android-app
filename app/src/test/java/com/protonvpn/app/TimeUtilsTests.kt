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

import com.protonvpn.android.utils.TimeUtils.getFormattedTimeFromSeconds
import org.junit.Assert
import org.junit.Test

class TimeUtilsTests {

    @Test
    fun testPeriodFormatter() {
        Assert.assertEquals("0s", getFormattedTimeFromSeconds(0))
        Assert.assertEquals("12s", getFormattedTimeFromSeconds(12))
        Assert.assertEquals("1m", getFormattedTimeFromSeconds(60))
        Assert.assertEquals("3h 5s", getFormattedTimeFromSeconds(3 * 60 * 60 + 5))
        Assert.assertEquals("1d 6h 5s", getFormattedTimeFromSeconds(30 * 60 * 60 + 5))
    }
}
