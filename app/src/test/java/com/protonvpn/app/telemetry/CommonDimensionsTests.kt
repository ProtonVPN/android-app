/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.app.telemetry

import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.ServerWrapper
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.telemetry.CommonDimensions
import com.protonvpn.android.ui.home.ServerListUpdaterPrefs
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import com.protonvpn.test.shared.MockedServers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CommonDimensionsTests {

    private lateinit var prefs: ServerListUpdaterPrefs
    private lateinit var vpnStateMonitor: VpnStateMonitor

    private lateinit var commonDimensions: CommonDimensions

    @Before
    fun setup() {
        prefs = ServerListUpdaterPrefs(MockSharedPreferencesProvider())
        vpnStateMonitor = VpnStateMonitor()

        commonDimensions = CommonDimensions(vpnStateMonitor, prefs)
    }

    @Test
    fun `add only requested dimensions`() {
        val empty = buildMap { commonDimensions.add(this) }
        assertTrue(empty.isEmpty())

        val some = buildMap { commonDimensions.add(this, CommonDimensions.Key.ISP, CommonDimensions.Key.USER_COUNTRY) }
        assertEquals(setOf("isp", "user_country"), some.keys)

        val all = buildMap { commonDimensions.add(this, *CommonDimensions.Key.values()) }
        assertEquals(setOf("isp", "user_country", "vpn_status"), all.keys)
    }

    @Test
    fun `values are correctly set`() {
        val connectionParams = ConnectionParams(
            Profile.getTempProfile(ServerWrapper.makePreBakedFastest()),
            MockedServers.server,
            null,
            null
        )
        prefs.lastKnownIsp = "some ISP"
        prefs.lastKnownCountry = "CH"
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, connectionParams))

        val expected = mapOf(
            "isp" to "some ISP",
            "user_country" to "CH",
            "vpn_status" to "on"
        )

        val dimensions = buildMap { commonDimensions.add(this, *CommonDimensions.Key.values()) }
        assertEquals(expected, dimensions)
    }
}
