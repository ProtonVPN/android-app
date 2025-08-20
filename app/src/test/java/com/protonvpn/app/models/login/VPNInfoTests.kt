/*
 * Copyright (c) 2021. Proton AG
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

package com.protonvpn.app.models.login

import com.protonvpn.android.models.login.VPNInfo
import org.junit.Assert
import org.junit.Test

class VPNInfoTests {

    @Test
    fun unknownTier() {
        val vpnInfo = vpnInfoWithTierSettings(null, null, 20)
        Assert.assertTrue(vpnInfo.userTierUnknown)
    }

    @Test
    fun tierAndPlanSet() {
        val vpnInfo = vpnInfoWithTierSettings("free", 0, 20)
        Assert.assertFalse(vpnInfo.userTierUnknown)
    }

    private fun vpnInfoWithTierSettings(tierName: String?, maxTier: Int?, maxConnect: Int) =
        VPNInfo(1000, 10, tierName, tierName, maxTier, maxConnect, "user", "groupId", "pass")
}
