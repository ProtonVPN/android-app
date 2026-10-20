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

package com.protonvpn.app.tv.countries

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.countries.ui.ServerGroupItemData
import com.protonvpn.android.redesign.countries.ui.ServerListViewModelDataAdapter
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.tv.countries.ui.CountriesGridFlow
import com.protonvpn.android.tv.countries.ui.CountryGridUiItem.Status
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createAccountUser
import com.protonvpn.test.shared.createServer
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class CountriesGridFlowTests {

    private lateinit var testScope: TestScope
    private lateinit var vpnStateMonitor: VpnStateMonitor
    private lateinit var gridFlow: CountriesGridFlow

    @Before
    fun setup() {
        testScope = TestScope(UnconfinedTestDispatcher())
        val adapter = mockk<ServerListViewModelDataAdapter>()
        every { adapter.countries(any(), any()) } returns flowOf(
            listOf(country("CH"), country("SE"), country("US", inMaintenance = true))
        )
        val testUserProvider = TestCurrentUserProvider(
            TestUser.plusUser.vpnUser,
            createAccountUser(TestUser.plusUser.vpnUser.userId)
        )
        vpnStateMonitor = VpnStateMonitor()
        gridFlow = CountriesGridFlow(
            context = mockk(relaxed = true),
            adapter = adapter,
            vpnStatusProviderUI = VpnStatusProviderUI(testScope.backgroundScope, vpnStateMonitor),
            currentUser = CurrentUser(testUserProvider),
        )
    }

    @Test
    fun `when disconnected then all countries are regular or in maintenance`() = testScope.runTest {
        assertEquals(
            mapOf("CH" to Status.Regular, "SE" to Status.Regular, "US" to Status.InMaintenance),
            observeStatuses()
        )
    }

    @Test
    fun `when connected to a country then only its tile is connected`() = testScope.runTest {
        setVpnState(VpnState.Connected, exitCountry = "SE")
        assertEquals(
            mapOf("CH" to Status.Regular, "SE" to Status.Connected, "US" to Status.InMaintenance),
            observeStatuses()
        )
    }

    @Test
    fun `when connecting then the target country tile is connecting`() = testScope.runTest {
        setVpnState(VpnState.Connecting, exitCountry = "CH")
        assertEquals(Status.Connecting, observeStatuses()["CH"])
    }

    @Test
    fun `when waiting for network then the target country tile is connecting`() = testScope.runTest {
        setVpnState(VpnState.WaitingForNetwork, exitCountry = "CH")
        assertEquals(Status.Connecting, observeStatuses()["CH"])
    }

    @Test
    fun `when disconnecting then the country tile is regular`() = testScope.runTest {
        setVpnState(VpnState.Disconnecting, exitCountry = "CH")
        assertEquals(Status.Regular, observeStatuses()["CH"])
    }

    @Test
    fun `connected status takes precedence over maintenance`() = testScope.runTest {
        setVpnState(VpnState.Connected, exitCountry = "US")
        assertEquals(Status.Connected, observeStatuses()["US"])
    }

    @Test
    fun `when connected to a UK server then the GB tile is connected`() = testScope.runTest {
        val adapter = mockk<ServerListViewModelDataAdapter>()
        every { adapter.countries(any(), any()) } returns flowOf(listOf(country("GB")))
        val flow = CountriesGridFlow(
            context = mockk(relaxed = true),
            adapter = adapter,
            vpnStatusProviderUI = VpnStatusProviderUI(testScope.backgroundScope, vpnStateMonitor),
            currentUser = CurrentUser(
                TestCurrentUserProvider(TestUser.plusUser.vpnUser, createAccountUser(TestUser.plusUser.vpnUser.userId))
            ),
        )
        setVpnState(VpnState.Connected, exitCountry = "UK")
        assertEquals(Status.Connected, flow.observeCountries(Locale.US).first().single().status)
    }

    private suspend fun observeStatuses(): Map<String, Status> =
        gridFlow.observeCountries(Locale.US).first().associate { it.countryId.countryCode to it.status }

    private fun setVpnState(state: VpnState, exitCountry: String) {
        val server = createServer(exitCountry = exitCountry)
        val intent = ConnectIntent.FastestInCountry(CountryId(exitCountry), emptySet())
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(state, ConnectionParams(intent, server, null, null)))
    }

    private fun country(code: String, inMaintenance: Boolean = false) = ServerGroupItemData.Country(
        countryId = CountryId(code),
        inMaintenance = inMaintenance,
        tier = 2,
        entryCountryId = null,
        cityStateCount = 1,
        hasStates = false,
    )
}
