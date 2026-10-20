/*
 * Copyright (c) 2026. Proton AG
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

package com.protonvpn.app.tv.vpn

import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.tv.vpn.ui.VpnStatusTvFlow
import com.protonvpn.android.tv.vpn.ui.VpnStatusTvViewState
import com.protonvpn.android.vpn.ErrorType
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.test.shared.createServer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class VpnStatusTvFlowTests {

    private lateinit var testScope: TestScope
    private lateinit var vpnStateMonitor: VpnStateMonitor
    private lateinit var statusFlow: VpnStatusTvFlow

    @Before
    fun setup() {
        testScope = TestScope(UnconfinedTestDispatcher())
        vpnStateMonitor = VpnStateMonitor()
        statusFlow = VpnStatusTvFlow(VpnStatusProviderUI(testScope.backgroundScope, vpnStateMonitor))
    }

    @Test
    fun `vpn states are mapped to unprotected, connecting and protected`() = testScope.runTest {
        val expected = listOf(
            VpnState.Disabled to VpnStatusTvViewState.Unprotected,
            VpnState.Disconnecting to VpnStatusTvViewState.Unprotected,
            VpnState.ScanningPorts to VpnStatusTvViewState.Connecting,
            VpnState.CheckingAvailability to VpnStatusTvViewState.Connecting,
            VpnState.WaitingForNetwork to VpnStatusTvViewState.Connecting,
            VpnState.Connecting to VpnStatusTvViewState.Connecting,
            VpnState.Reconnecting to VpnStatusTvViewState.Connecting,
            VpnState.Error(ErrorType.UNREACHABLE, isFinal = false) to VpnStatusTvViewState.Connecting,
            VpnState.Error(ErrorType.UNREACHABLE, isFinal = true) to VpnStatusTvViewState.Unprotected,
            VpnState.Connected to VpnStatusTvViewState.Protected,
        )
        val actual = expected.map { (vpnState, _) ->
            setVpnState(vpnState)
            vpnState to statusFlow.observe().first()
        }
        assertEquals(expected, actual)
    }

    @Test
    fun `nothing is shown while the vpn state is loading`() = testScope.runTest {
        setVpnState(VpnState.Loading)
        assertEquals(null, statusFlow.observe().first())
    }

    private fun setVpnState(state: VpnState) {
        val intent = ConnectIntent.FastestInCountry(CountryId("CH"), emptySet())
        val params = ConnectionParams(intent, createServer(exitCountry = "CH"), null, null)
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(state, params))
    }
}
