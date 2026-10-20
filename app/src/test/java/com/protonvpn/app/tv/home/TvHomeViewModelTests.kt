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

package com.protonvpn.app.tv.home

import com.protonvpn.android.redesign.CityStateId
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.tv.countries.ui.CountryConnectMenuOption
import com.protonvpn.android.tv.countries.ui.CountryConnectMenuOption.Type
import com.protonvpn.android.tv.countries.ui.CountryGridUiItem
import com.protonvpn.android.tv.home.TvHomeViewModel
import com.protonvpn.android.tv.usecases.TvUiConnectDisconnectHelper
import com.protonvpn.android.vpn.VpnConnect
import com.protonvpn.mocks.FakeVpnUiDelegate
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TvHomeViewModelTests {

    private lateinit var testScope: TestScope
    private lateinit var connectDisconnectHelper: TvUiConnectDisconnectHelper
    private lateinit var viewModel: TvHomeViewModel

    private val connectedIntents = mutableListOf<AnyConnectIntent>()
    private val upsellCountries = mutableListOf<CountryId>()
    private var menuClosed = false

    private val countryId = CountryId("US")

    @Before
    fun setup() {
        val testDispatcher = UnconfinedTestDispatcher()
        testScope = TestScope(testDispatcher)
        Dispatchers.setMain(testDispatcher)
        connectDisconnectHelper = mockk { every { disconnect(any()) } just Runs }
        viewModel = TvHomeViewModel(
            countriesGridFlow = mockk(),
            countryConnectMenuFlow = mockk(),
            connect = VpnConnect { _, intent, _ -> connectedIntents += intent },
            connectDisconnectHelper = connectDisconnectHelper,
            vpnStatusTvFlow = mockk { every { observe() } returns flowOf(null) },
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `clicking fastest connects to the fastest server in the country`() = testScope.runTest {
        click(option(Type.Fastest))
        assertEquals(listOf(ConnectIntent.FastestInCountry(countryId, emptySet())), connectedIntents)
        assertTrue(menuClosed)
    }

    @Test
    fun `clicking a state connects to the fastest server in that state`() = testScope.runTest {
        click(option(Type.CityState(CityStateId("California", isState = true), "California")))
        assertEquals(listOf(ConnectIntent.FastestInState(countryId, "California", emptySet())), connectedIntents)
    }

    @Test
    fun `clicking a city connects to the fastest server in that city`() = testScope.runTest {
        click(option(Type.CityState(CityStateId("Zurich", isState = false), "Zürich")))
        assertEquals(listOf(ConnectIntent.FastestInCity(countryId, "Zurich", emptySet())), connectedIntents)
    }

    @Test
    fun `clicking the connected option disconnects`() = testScope.runTest {
        click(option(Type.Fastest, connected = true))
        verify { connectDisconnectHelper.disconnect(any()) }
        assertEquals(emptyList<AnyConnectIntent>(), connectedIntents)
        assertTrue(menuClosed)
    }

    @Test
    fun `clicking an unavailable option opens upsell and keeps the menu open`() = testScope.runTest {
        click(option(Type.Random, available = false))
        assertEquals(listOf(countryId), upsellCountries)
        assertEquals(emptyList<AnyConnectIntent>(), connectedIntents)
        assertFalse(menuClosed)
    }

    @Test
    fun `clicking an option in maintenance does nothing`() = testScope.runTest {
        click(option(Type.Fastest, inMaintenance = true))
        assertEquals(emptyList<AnyConnectIntent>(), connectedIntents)
        assertEquals(emptyList<CountryId>(), upsellCountries)
        assertFalse(menuClosed)
    }

    @Test
    fun `clicking an available country connects to its fastest server`() = testScope.runTest {
        clickCountry(gridItem())
        assertEquals(listOf(ConnectIntent.FastestInCountry(countryId, emptySet())), connectedIntents)
    }

    @Test
    fun `clicking an unavailable country opens upsell for that country`() = testScope.runTest {
        clickCountry(gridItem(available = false))
        assertEquals(listOf(countryId), upsellCountries)
        assertEquals(emptyList<AnyConnectIntent>(), connectedIntents)
    }

    @Test
    fun `clicking a country in maintenance doesn't connect`() = testScope.runTest {
        clickCountry(gridItem(status = CountryGridUiItem.Status.InMaintenance))
        assertEquals(emptyList<AnyConnectIntent>(), connectedIntents)
        assertEquals(emptyList<CountryId>(), upsellCountries)
    }

    private fun clickCountry(country: CountryGridUiItem) {
        viewModel.onCountryClick(FakeVpnUiDelegate(), country, launchUpsell = { upsellCountries += it })
    }

    private fun gridItem(
        available: Boolean = true,
        status: CountryGridUiItem.Status = CountryGridUiItem.Status.Regular,
    ) = CountryGridUiItem(
        countryId = countryId,
        countryName = "United States",
        flagResource = 0,
        status = status,
        available = available,
        tier = if (available) 0 else 2,
        cityStateCount = 2,
        hasStates = true,
    )

    private fun click(option: CountryConnectMenuOption) {
        viewModel.onConnectMenuOptionClick(
            vpnUiDelegate = FakeVpnUiDelegate(),
            countryId = countryId,
            option = option,
            launchUpsell = { upsellCountries += it },
            closeMenu = { menuClosed = true },
        )
    }

    private fun option(
        type: Type,
        available: Boolean = true,
        inMaintenance: Boolean = false,
        connected: Boolean = false,
    ) = CountryConnectMenuOption(type, available, inMaintenance, connected)
}
