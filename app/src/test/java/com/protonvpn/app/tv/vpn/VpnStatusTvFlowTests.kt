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

import app.cash.turbine.test
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.countries.Translator
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.servers.Server
import com.protonvpn.android.tv.vpn.ui.VpnStatusTvFlow
import com.protonvpn.android.tv.vpn.ui.VpnStatusTvViewState
import com.protonvpn.android.tv.vpn.ui.VpnStatusTvViewState.Location
import com.protonvpn.android.tv.vpn.ui.VpnStatusTvViewState.Status
import com.protonvpn.android.ui.home.ServerListUpdaterPrefs
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.vpn.ErrorType
import com.protonvpn.android.vpn.IpPair
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import com.protonvpn.test.shared.createServer
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Locale

class VpnStatusTvFlowTests {

    private lateinit var testScope: TestScope
    private lateinit var vpnStateMonitor: VpnStateMonitor
    private lateinit var serverListUpdaterPrefs: ServerListUpdaterPrefs
    private lateinit var statusFlow: VpnStatusTvFlow

    private val usServer = createServer(exitCountry = "US", city = "Los Angeles", state = "California")
    private val physicalLocation = Location(countryName("PL"), "1.2.3.4")

    @Before
    fun setup() {
        testScope = TestScope(UnconfinedTestDispatcher())
        vpnStateMonitor = VpnStateMonitor()
        serverListUpdaterPrefs = ServerListUpdaterPrefs(MockSharedPreferencesProvider())
        serverListUpdaterPrefs.lastKnownCountry = "PL"
        serverListUpdaterPrefs.ipAddress = "1.2.3.4"
        statusFlow = VpnStatusTvFlow(
            vpnStatusProviderUI = VpnStatusProviderUI(testScope.backgroundScope, vpnStateMonitor),
            vpnStateMonitor = vpnStateMonitor,
            serverListUpdaterPrefs = serverListUpdaterPrefs,
            translator = mockk<Translator> { every { flow } returns MutableStateFlow(null) },
        )
    }

    @Test
    fun `vpn states are mapped to unprotected, connecting and protected`() = testScope.runTest {
        val expected = listOf(
            VpnState.Disabled to Status.Unprotected,
            VpnState.Disconnecting to Status.Unprotected,
            VpnState.ScanningPorts to Status.Connecting,
            VpnState.CheckingAvailability to Status.Connecting,
            VpnState.WaitingForNetwork to Status.Connecting,
            VpnState.Connecting to Status.Connecting,
            VpnState.Reconnecting to Status.Connecting,
            VpnState.Error(ErrorType.UNREACHABLE, isFinal = false) to Status.Connecting,
            VpnState.Error(ErrorType.UNREACHABLE, isFinal = true) to Status.Unprotected,
            VpnState.Connected to Status.Protected,
        )
        val actual = expected.map { (vpnState, _) ->
            setVpnState(vpnState)
            vpnState to statusFlow.observeStatus().first()
        }
        assertEquals(expected, actual)
    }

    @Test
    fun `nothing is shown while the vpn state is loading`() = testScope.runTest {
        setVpnState(VpnState.Loading)
        assertNull(statusFlow.observeStatus().first())
        assertNull(statusFlow.observe(Locale.US).first())
    }

    @Test
    fun `unprotected shows the physical location`() = testScope.runTest {
        setVpnState(VpnState.Disabled)
        assertEquals(VpnStatusTvViewState(Status.Unprotected, physicalLocation), statusFlow.observe(Locale.US).first())
    }

    @Test
    fun `connecting shows the physical location`() = testScope.runTest {
        setVpnState(VpnState.Connecting)
        assertEquals(VpnStatusTvViewState(Status.Connecting, physicalLocation), statusFlow.observe(Locale.US).first())
    }

    @Test
    fun `protected shows the vpn country and exit ip`() = testScope.runTest {
        vpnStateMonitor.updateLastKnownExitIp(IpPair("5.6.7.8", null))
        setVpnState(VpnState.Connected)
        assertEquals(Location(countryName("CH"), "5.6.7.8"), statusFlow.observe(Locale.US).first()?.location)
    }

    @Test
    fun `protected without a known exit ip shows only the country`() = testScope.runTest {
        setVpnState(VpnState.Connected)
        assertEquals(Location(countryName("CH"), null), statusFlow.observe(Locale.US).first()?.location)
    }

    @Test
    fun `protected city connection shows the city`() = testScope.runTest {
        vpnStateMonitor.updateLastKnownExitIp(IpPair("5.6.7.8", null))
        setVpnState(VpnState.Connected, ConnectIntent.FastestInCity(CountryId("CH"), "Zurich", emptySet()))
        assertEquals(Location("${countryName("CH")} - Zurich", "5.6.7.8"), statusFlow.observe(Locale.US).first()?.location)
    }

    @Test
    fun `protected state connection shows the state`() = testScope.runTest {
        vpnStateMonitor.updateLastKnownExitIp(IpPair("5.6.7.8", null))
        setVpnState(
            VpnState.Connected,
            ConnectIntent.FastestInState(CountryId("US"), "California", emptySet()),
            usServer,
        )
        assertEquals(
            Location("${countryName("US")} - California", "5.6.7.8"),
            statusFlow.observe(Locale.US).first()?.location
        )
    }

    @Test
    fun `protected country connection doesn't show the city or state`() = testScope.runTest {
        vpnStateMonitor.updateLastKnownExitIp(IpPair("5.6.7.8", null))
        setVpnState(VpnState.Connected, ConnectIntent.FastestInCountry(CountryId("US"), emptySet()), usServer)
        assertEquals(Location(countryName("US"), "5.6.7.8"), statusFlow.observe(Locale.US).first()?.location)
    }

    @Test
    fun `protected location is updated when the exit ip becomes known`() = testScope.runTest {
        setVpnState(VpnState.Connected)
        statusFlow.observe(Locale.US).test {
            assertEquals(Location(countryName("CH"), null), awaitItem()?.location)
            vpnStateMonitor.updateLastKnownExitIp(IpPair("5.6.7.8", null))
            assertEquals(Location(countryName("CH"), "5.6.7.8"), awaitItem()?.location)
        }
    }

    private fun setVpnState(
        state: VpnState,
        intent: ConnectIntent = ConnectIntent.FastestInCountry(CountryId("CH"), emptySet()),
        server: Server = createServer(exitCountry = "CH", city = "Zurich"),
    ) {
        val params = ConnectionParams(intent, server, null, null)
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(state, params))
    }

    private fun countryName(code: String) = CountryTools.getFullName(Locale.US, code)
}
