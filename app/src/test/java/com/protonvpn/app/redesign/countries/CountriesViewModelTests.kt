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

package com.protonvpn.app.redesign.countries

import androidx.lifecycle.SavedStateHandle
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.servers.api.SERVER_FEATURE_TOR
import com.protonvpn.android.redesign.CityStateId
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.countries.Translator
import com.protonvpn.android.redesign.countries.ui.CitiesScreenSaveState
import com.protonvpn.android.redesign.countries.ui.CitiesScreenState
import com.protonvpn.android.redesign.countries.ui.CountriesViewModel
import com.protonvpn.android.redesign.countries.ui.ServerFilterType
import com.protonvpn.android.redesign.countries.ui.ServerGroupItemData
import com.protonvpn.android.redesign.countries.ui.ServerGroupUiItem
import com.protonvpn.android.redesign.countries.ui.ServerGroupsMainScreenSaveState
import com.protonvpn.android.redesign.countries.ui.ServerGroupsMainScreenState
import com.protonvpn.android.redesign.countries.ui.ServerListViewModelDataAdapter
import com.protonvpn.android.redesign.main_screen.ui.ShouldShowcaseRecents
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.VpnConnect
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.app.testRules.RobolectricHiltAndroidRule
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createServer
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.mockk
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.ArrayDeque
import java.util.Locale
import javax.inject.Inject

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
class CountriesViewModelTests {

    @get:Rule
    val roboRule = RobolectricHiltAndroidRule(this)

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var currentUserProvider: TestCurrentUserProvider

    @Inject
    lateinit var vpnStateMonitor: VpnStateMonitor

    @Inject
    lateinit var countriesViewModelInjector: CountriesViewModelInjector

    private lateinit var viewModel: CountriesViewModel

    private val localePl = Locale("PL", "PL")

    @Before
    fun setup() {
        roboRule.inject()
        currentUserProvider.vpnUser = TestUser.plusUser.vpnUser
        viewModel = countriesViewModelInjector.getViewModel()
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
        state.assertCountries(listOf("LV", "RO"), shouldHaveFastest = true)
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

    @Test
    fun selectFilterScenario() = runTest {
        val servers = listOf(
            createServer(exitCountry = "US"),
            createServer(exitCountry = "JP", features = SERVER_FEATURE_TOR),
        )
        serverManager.setServers(servers, null)
        viewModel.localeFlow.value = Locale.US

        val expectedFilters = listOf(ServerFilterType.All, ServerFilterType.Tor)
        viewModel.stateFlow.filterNotNull().take(2).collectIndexed { i, state ->
            when (i) {
                0 -> {
                    state.assertCountries(listOf("JP", "US"), ServerFilterType.All, expectedFilters, shouldHaveFastest = true)

                    // Click on Tor
                    state.filterButtons.first { it.filter == ServerFilterType.Tor }.onClick()
                }
                1 -> {
                    // No fastest expected for single item list
                    state.assertCountries(listOf("JP"), ServerFilterType.Tor, expectedFilters, shouldHaveFastest = false)
                }
            }
        }
    }

    @Test
    fun connectTest() = runTest {
        var connectCalled = false
        val viewModel = countriesViewModelInjector.getViewModel(connect = { _, _, _ -> connectCalled = true })
        serverManager.setServers(
            listOf(createServer(exitCountry = "US"), createServer(exitCountry = "JP")),
            null,
        )
        viewModel.localeFlow.value = localePl

        val state = viewModel.stateFlow.filterNotNull().first()
        val last = state.items.last() as ServerGroupUiItem.ServerGroup

        var homeNavigated = false
        viewModel.onItemConnect(mockk(), last, state.selectedFilter, navigateToUpsell = {}, navigateToHome = {
            homeNavigated = true
        })
        assertTrue(homeNavigated)
        assertTrue(connectCalled)
    }

    @Inject
    lateinit var testScope: TestScope

    @Test
    fun freeUserCountriesNotAvailable() = runTest {
        currentUserProvider.vpnUser = TestUser.freeUser.vpnUser
        var connectCalled = false
        val viewModel = countriesViewModelInjector.getViewModel(
            connect = { _, _, _ -> connectCalled = true }
        )
        serverManager.setServers(
            listOf(createServer(exitCountry = "US"), createServer(exitCountry = "JP")),
            null,
        )
        viewModel.localeFlow.value = localePl

        val state = viewModel.stateFlow.filterNotNull().first()
        state.assertCountries(
            listOf("JP", "US"),
            shouldHaveFastest = false,
            expectedBanner = ServerGroupUiItem.BannerType.Countries,
            countriesAvailable = false,
        )

        // Connecting navigates to upsell
        val last = state.items.last() as ServerGroupUiItem.ServerGroup
        var navigatedToUpsell = false
        viewModel.onItemConnect(mockk(), last, state.selectedFilter, {}) {
            navigatedToUpsell = true
        }
        assertTrue(navigatedToUpsell)
        assertFalse(connectCalled)
    }

    @Test
    fun stateRestoreTest() = runTest {
        val mainSavedState = ServerGroupsMainScreenSaveState(ServerFilterType.Tor)
        val viewModel = countriesViewModelInjector.getViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "country_list:main_screen_state" to mainSavedState,
                    "country_list:sub_screen_state" to CitiesScreenSaveState(CountryId("JP"), ServerFilterType.Tor)
                )
            )
        )
        serverManager.setServers(
            listOf(createServer(exitCountry = "US"), createServer(exitCountry = "JP", features = SERVER_FEATURE_TOR)),
            null,
        )
        viewModel.localeFlow.value = localePl

        val state = viewModel.stateFlow.filterNotNull().first()
        state.assertCountries(listOf("JP"), ServerFilterType.Tor, shouldHaveFastest = false,
            expectedFilterButtons = listOf(ServerFilterType.All, ServerFilterType.Tor))

        val subState = viewModel.subScreenStateFlow.filterNotNull().first()
        assertEquals("JP", (subState as CitiesScreenState).countryId.countryCode)
    }

    @Test
    fun freeUserCanOpenCountry() = runTest {
        currentUserProvider.vpnUser = TestUser.freeUser.vpnUser
        val viewModel = countriesViewModelInjector.getViewModel()
        serverManager.setServers(
            listOf(createServer(exitCountry = "US"), createServer(exitCountry = "JP")),
            null,
        )
        viewModel.localeFlow.value = localePl

        val state = viewModel.stateFlow.filterNotNull().first()
        val last = state.items.last() as ServerGroupUiItem.ServerGroup
        viewModel.onItemOpen(last, state.selectedFilter)

        val subState = viewModel.subScreenStateFlow.filterNotNull().first()
        assertEquals("US", (subState as CitiesScreenState).countryId.countryCode)
    }

    @Test
    fun connectedCountryIsMarkedAsConnected() = testScope.runTest {
        var connectRequested = false
        val usServer = createServer(exitCountry = "US")
        val connectionParams = ConnectionParams(ConnectIntent.FastestInCountry(CountryId("US"), setOf()), usServer, null, null)
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, connectionParams))
        val viewModel = countriesViewModelInjector.getViewModel(
            connect = { _, _, _ -> connectRequested = true }
        )
        serverManager.setServers(
            listOf(usServer, createServer(exitCountry = "JP")),
            null,
        )
        viewModel.localeFlow.value = localePl

        val state = viewModel.stateFlow.filterNotNull().first()
        val countries = state.items.filterIsInstance<ServerGroupUiItem.ServerGroup>()
        val connectedCountries = countries.filter { it.connected }.map { it.data.countryId?.countryCode }
        assertEquals(listOf("US"), connectedCountries)

        // Clicking on connected country just navigates home
        var homeNavigated = false
        viewModel.onItemConnect(mockk(), countries.first { it.connected }, state.selectedFilter,
            { homeNavigated = true }, {})
        assertTrue(homeNavigated)
        assertFalse(connectRequested)
    }

    private fun ServerGroupsMainScreenState.assertCountries(
        expectedCountries: List<String>,
        expectedFilter: ServerFilterType = ServerFilterType.All,
        expectedFilterButtons: List<ServerFilterType> = listOf(ServerFilterType.All),
        shouldHaveFastest: Boolean,
        expectedBanner : ServerGroupUiItem.BannerType? = null,
        countriesAvailable: Boolean = true,
    ) {
        val itemsQueue = ArrayDeque(items)
        val header = itemsQueue.pop() as ServerGroupUiItem.Header
        if (expectedBanner != null) {
            val banner = itemsQueue.pop() as ServerGroupUiItem.Banner
            assertEquals(expectedBanner, banner.type)
        }
        val countries = itemsQueue.toList() as List<ServerGroupUiItem.ServerGroup>

        assertEquals(expectedFilter, selectedFilter)
        assertEquals(expectedCountries.size, header.count) // Header shouldn't be included
        assertEquals(
            (if (shouldHaveFastest) listOf(CountryId.fastest) else emptyList()) + expectedCountries.map { CountryId(it) },
            countries.map { it.data.countryId }
        )
        assertEquals(shouldHaveFastest, countries.any { it.data.countryId == CountryId.fastest })
        assertEquals(expectedFilterButtons, filterButtons.map { it.filter })
        assertTrue(countries.all { it.available == countriesAvailable })
    }
}

class CountriesViewModelInjector @Inject constructor(
    private val adapter: ServerListViewModelDataAdapter,
    private val currentUser: CurrentUser,
    private val shouldShowcaseRecents: ShouldShowcaseRecents,
    private val vpnStatusProviderUI: VpnStatusProviderUI,
    private val translator: Translator
) {
    fun getViewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        connect: VpnConnect = VpnConnect { _, _, _ -> },
    ) = CountriesViewModel(
        savedStateHandle,
        dataAdapter = adapter,
        connect = connect,
        shouldShowcaseRecents = shouldShowcaseRecents,
        currentUser = currentUser,
        vpnStatusProviderUI = vpnStatusProviderUI,
        translator = translator,
    )
}
