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

import android.content.Context
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.redesign.CityStateId
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.countries.Translator
import com.protonvpn.android.redesign.countries.ui.ServerGroupItemData
import com.protonvpn.android.redesign.countries.ui.ServerListViewModelDataAdapter
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.tv.countries.ui.CountryConnectMenuFlow
import com.protonvpn.android.tv.countries.ui.CountryConnectMenuOption
import com.protonvpn.android.tv.countries.ui.CountryConnectMenuOption.Type
import com.protonvpn.android.tv.countries.ui.CountryConnectMenuUiState
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createAccountUser
import com.protonvpn.test.shared.createServer
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Locale

class CountryConnectMenuFlowTests {

    private lateinit var testScope: TestScope
    private lateinit var adapter: ServerListViewModelDataAdapter
    private lateinit var testUserProvider: TestCurrentUserProvider
    private lateinit var vpnStateMonitor: VpnStateMonitor
    private lateinit var menuFlow: CountryConnectMenuFlow

    private val countryId = CountryId("US")

    @Before
    fun setup() {
        testScope = TestScope(UnconfinedTestDispatcher())
        adapter = mockk()
        every { adapter.countries(any(), any()) } returns flowOf(
            listOf(country(countryId, tier = 0), country(CountryId("CH"), tier = 2))
        )
        every { adapter.cities(any(), countryId, any(), true) } returns flowOf(
            listOf(state("New York", tier = 2), state("California", tier = 0), state("Texas", inMaintenance = true))
        )
        val translator = mockk<Translator> { every { flow } returns MutableStateFlow(null) }
        testUserProvider = TestCurrentUserProvider(
            TestUser.plusUser.vpnUser,
            createAccountUser(TestUser.plusUser.vpnUser.userId)
        )
        vpnStateMonitor = VpnStateMonitor()
        menuFlow = CountryConnectMenuFlow(
            context = mockk(relaxed = true),
            adapter = adapter,
            translator = translator,
            vpnStatusProviderUI = VpnStatusProviderUI(testScope.backgroundScope, vpnStateMonitor),
            currentUser = CurrentUser(testUserProvider),
        )
    }

    @Test
    fun `menu lists fastest, random and then cities or states sorted by name`() = testScope.runTest {
        val state = observe()
        assertEquals(
            listOf(Type.Fastest, Type.Random, cityState("California"), cityState("New York"), cityState("Texas")),
            state.options.map { it.type }
        )
    }

    @Test
    fun `plus user can choose any location and maintenance is reported`() = testScope.runTest {
        val options = observe().options
        assertEquals(listOf(true, true, true, true, true), options.map { it.available })
        assertEquals(listOf(false, false, false, false, true), options.map { it.inMaintenance })
    }

    @Test
    fun `free user can only connect to the fastest server`() = testScope.runTest {
        testUserProvider.vpnUser = TestUser.freeUser.vpnUser
        val options = observe().options
        assertEquals(listOf(true, false, false, false, false), options.map { it.available })
    }

    @Test
    fun `free user can't connect to a paid country`() = testScope.runTest {
        testUserProvider.vpnUser = TestUser.freeUser.vpnUser
        every { adapter.cities(any(), CountryId("CH"), any(), true) } returns flowOf(emptyList())
        val options = menuFlow.observe(CountryId("CH"), Locale.US).first()!!.options
        assertEquals(listOf(false, false), options.map { it.available })
    }

    @Test
    fun `connected dot is on the fastest option when connected to the fastest server in the country`() =
        testScope.runTest {
            setConnected(ConnectIntent.FastestInCountry(countryId, emptySet()))
            assertEquals(listOf(Type.Fastest), observe().connectedTypes())
        }

    @Test
    fun `connected dot is on the state the user is connected to`() = testScope.runTest {
        setConnected(ConnectIntent.FastestInState(countryId, "New York", emptySet()))
        assertEquals(listOf(cityState("New York")), observe().connectedTypes())
    }

    @Test
    fun `no connected dot when connected to another country`() = testScope.runTest {
        setConnected(ConnectIntent.FastestInCountry(CountryId("CH"), emptySet()))
        assertEquals(emptyList<Type>(), observe().connectedTypes())
    }

    @Test
    fun `no connected dot while connecting`() = testScope.runTest {
        setConnected(ConnectIntent.FastestInCountry(countryId, emptySet()), VpnState.Connecting)
        assertEquals(emptyList<Type>(), observe().connectedTypes())
    }

    @Test
    fun `menu is null for a country without servers`() = testScope.runTest {
        every { adapter.cities(any(), CountryId("PL"), any(), true) } returns flowOf(emptyList())
        assertNull(menuFlow.observe(CountryId("PL"), Locale.US).first())
    }

    private suspend fun observe(): CountryConnectMenuUiState = menuFlow.observe(countryId, Locale.US).first()!!

    private fun setConnected(intent: ConnectIntent, state: VpnState = VpnState.Connected) {
        val server = createServer(exitCountry = "US", state = "New York")
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(state, ConnectionParams(intent, server, null, null)))
    }

    private fun CountryConnectMenuUiState.connectedTypes() = options.filter { it.connected }.map { it.type }

    private fun cityState(name: String) = Type.CityState(CityStateId(name, isState = true), name)

    private fun country(id: CountryId, tier: Int) = ServerGroupItemData.Country(
        countryId = id,
        inMaintenance = false,
        tier = tier,
        entryCountryId = null,
        cityStateCount = 3,
        hasStates = true,
    )

    private fun state(name: String, tier: Int = 2, inMaintenance: Boolean = false) = ServerGroupItemData.City(
        countryId = countryId,
        inMaintenance = inMaintenance,
        tier = tier,
        cityStateId = CityStateId(name, isState = true),
        name = name,
    )
}
