/*
 * Copyright (c) 2024. Proton AG
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

package com.protonvpn.tests.server_list

import androidx.lifecycle.SavedStateHandle
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.redesign.CityStateId
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.countries.Translator
import com.protonvpn.android.redesign.countries.ui.CountriesViewModel
import com.protonvpn.android.redesign.countries.ui.ServerFilterType
import com.protonvpn.android.redesign.countries.ui.ServerGroupItemData
import com.protonvpn.android.redesign.countries.ui.ServerGroupUiItem
import com.protonvpn.android.redesign.countries.ui.ServerListViewModelDataAdapter
import com.protonvpn.android.redesign.main_screen.ui.ShouldShowcaseRecents
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.test.shared.createServer
import com.protonvpn.testRules.CommonRuleChains.mockedLoggedInRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Locale
import javax.inject.Inject

@HiltAndroidTest
class CountriesViewModelTests {

    @get:Rule
    val rule = mockedLoggedInRule()

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var serverListDataAdapter: ServerListViewModelDataAdapter

    @Inject
    lateinit var vpnConnectionManager: VpnConnectionManager

    @Inject
    lateinit var shouldShowcaseRecents: ShouldShowcaseRecents

    @Inject
    lateinit var currentUser: CurrentUser

    @Inject
    lateinit var vpnStatusProviderUI: VpnStatusProviderUI

    @Inject
    lateinit var translator: Translator

    lateinit var viewModel: CountriesViewModel

    private val localePl = Locale("PL", "PL")

    @Before
    fun setup() {
        // Couldn't figure out how to inject the view model via Hilt.
        viewModel = CountriesViewModel(
            SavedStateHandle(),
            serverListDataAdapter,
            vpnConnectionManager,
            shouldShowcaseRecents,
            currentUser,
            vpnStatusProviderUI,
            translator
        )
    }

    @Test
    fun countriesOrderedWithLocale() = runTest {
        val servers = listOf(
            createServer(exitCountry = "RO", tier = 1),
            createServer(exitCountry = "LV", tier = 1), // Starts with Ł in Polish, should be in front of R.
        )
        serverManager.setServers(servers, null)
        viewModel.localeFlow.value = localePl

        val state = viewModel.stateFlow.filterNotNull().first()
        val countryItems = state.items.filterIsInstance<ServerGroupUiItem.ServerGroup>()
        val expected = listOf(
            CountryId.fastest, CountryId("LV"), CountryId("RO")
        )
        assertEquals(expected, countryItems.map { it.data.countryId })
    }

    @Test
    fun serversOrderedByLoad() = runTest {
        val city = "Warsaw"
        val servers = listOf(
            createServer(serverName = "full", exitCountry = "PL", city = city, loadPercent = 100f, tier = 1),
            createServer(serverName = "high", exitCountry = "PL", city = city, loadPercent = 50.13f, tier = 1),
            createServer(serverName = "low", exitCountry = "PL", city = city, loadPercent = 6.24f, tier = 1),
        )
        serverManager.setServers(servers, null)
        viewModel.localeFlow.value = localePl

        val cityItem = ServerGroupItemData.City(
            CountryId("PL"),
            cityStateId = CityStateId(city, false),
            name = city,
            inMaintenance = false,
            tier = 1
        )
        viewModel.onItemOpen(
            ServerGroupUiItem.ServerGroup(data = cityItem, available = true, connected = false),
            ServerFilterType.All
        )
        val state = viewModel.subScreenStateFlow.filterNotNull().first()
        val serverItems = state.items.filterIsInstance<ServerGroupUiItem.ServerGroup>()
        val serverItemDatas = serverItems.map { it.data }.filterIsInstance<ServerGroupItemData.Server>()
        assertEquals(listOf("low", "high", "full"), serverItemDatas.map { it.name })
    }
}
