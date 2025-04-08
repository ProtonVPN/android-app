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

package com.protonvpn.app.redesign.search

import androidx.lifecycle.SavedStateHandle
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.redesign.countries.Translator
import com.protonvpn.android.redesign.countries.ui.ServerGroupUiItem
import com.protonvpn.android.redesign.countries.ui.ServerListViewModelDataAdapter
import com.protonvpn.android.redesign.main_screen.ui.ShouldShowcaseRecents
import com.protonvpn.android.redesign.search.ui.SearchViewModel
import com.protonvpn.android.redesign.search.ui.SearchViewModelDataAdapter
import com.protonvpn.android.redesign.search.ui.SearchViewState
import com.protonvpn.android.redesign.search.ui.TextMatch
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.VpnConnect
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.app.redesign.countries.server
import com.protonvpn.app.testRules.RobolectricHiltAndroidRule
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.ArrayDeque
import java.util.Locale
import javax.inject.Inject
import kotlin.test.Test
import kotlin.test.assertIs

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
class SearchViewModelTests {

    @get:Rule
    val roboRule = RobolectricHiltAndroidRule(this)

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var currentUserProvider: TestCurrentUserProvider

    @Inject
    lateinit var searchViewModelInjector: SearchViewModelInjector

    private lateinit var viewModel: SearchViewModel

    @Before
    fun setup() {
        roboRule.inject()
        currentUserProvider.vpnUser = TestUser.plusUser.vpnUser
        viewModel = searchViewModelInjector.getViewModel()
    }

    @Test
    fun `search with multiple category matches`() = runTest {
        serverManager.setServers(
            listOf(
                server(exitCountry = "US", city = "Portland", serverName = "US#1"),
                server(exitCountry = "PT", city = "Porto", serverName = "PO#1"),
                server(exitCountry = "PL", city = "Warsaw", serverName = "PL#1"),
            ),
            null
        )
        viewModel.localeFlow.value = Locale.US
        viewModel.setQuery("po")

        val state = viewModel.stateFlow.filterIsInstance<SearchViewState.Result>().first()
        state.assertSearchResult(
            expectedCountries = listOf("Poland", "Portugal"),
            expectedCities = listOf("Portland", "Porto"),
            expectedServers = listOf("PO#1")
        )
    }

    @Test
    fun `first word match takes precedence over alphabetical order`() = runTest {
        serverManager.setServers(
            listOf(
                server(exitCountry = "EN", city = "York"),
                server(exitCountry = "US", city = "New York"),
            ),
            null
        )
        viewModel.localeFlow.value = Locale.US
        viewModel.setQuery("yor")

        val state = viewModel.stateFlow.filterIsInstance<SearchViewState.Result>().first()
        state.assertSearchResult(
            expectedCities = listOf("York", "New York"),
        )
    }

    @Test
    fun `match server names`() = runTest {
        fun assertTextMatch(expected: TextMatch, state: SearchViewState.Result) {
            // First item is the header, the second item is the matching server.
            assertEquals(2, state.result.items.size)
            val result = state.result.items[1]
            assertIs<ServerGroupUiItem.ServerGroup>(result)
            assertEquals(expected, result.data.textMatch)
        }

        serverManager.setServers(
            listOf(server(exitCountry = "US", city = "Portland", serverName = "US-CA#10")),
            null
        )
        viewModel.localeFlow.value = Locale.US

        viewModel.setQuery("us-ca#1")
        var state = viewModel.stateFlow.filterIsInstance<SearchViewState.Result>().first()
        state.assertSearchResult(
            expectedServers = listOf("US-CA#10")
        )

        viewModel.setQuery("us-ca1")
        // Assert that the highlight span accounts for the #.
        assertTextMatch(
            TextMatch(0, 7, "US-CA#10"),
            viewModel.stateFlow.filterIsInstance<SearchViewState.Result>().first()
        )

        viewModel.setQuery("ca#1")
        assertTextMatch(
            TextMatch(3, 4, "US-CA#10"),
            viewModel.stateFlow.filterIsInstance<SearchViewState.Result>().first()
        )

        viewModel.setQuery("ca1")
        // Assert that the highlight span accounts for the #.
        assertTextMatch(
            TextMatch(3, 4, "US-CA#10"),
            viewModel.stateFlow.filterIsInstance<SearchViewState.Result>().first()
        )
    }

    private fun SearchViewState.Result.assertSearchResult(
        expectedCountries: List<String> = emptyList(),
        expectedCities: List<String> = emptyList(),
        expectedStates: List<String> = emptyList(),
        expectedServers: List<String> = emptyList(),
        expectedGateways: List<String> = emptyList(),
    ) {
        ArrayDeque(result.items).run {
            assertSection(expectedCountries)
            assertSection(expectedCities)
            assertSection(expectedStates)
            assertSection(expectedServers)
            assertSection(expectedGateways)
        }
    }

    private fun ArrayDeque<ServerGroupUiItem>.assertSection(
        expectedLabels: List<String>
    ) {
        if (expectedLabels.isNotEmpty()) {
            val header = pop() as ServerGroupUiItem.Header
            val items = popMultiple<ServerGroupUiItem.ServerGroup>(this, header.count)

            Assert.assertEquals(expectedLabels.size, header.count)
            Assert.assertEquals(expectedLabels, items.map { it.data.textMatch?.fullText })
        }
    }
}

private fun <T> popMultiple(items: ArrayDeque<ServerGroupUiItem>, count: Int): List<T> =
    buildList { repeat(count) { add(items.pop() as T) } }

class SearchViewModelInjector @Inject constructor(
    private val adapter: ServerListViewModelDataAdapter,
    private val searchAdapter: SearchViewModelDataAdapter,
    private val currentUser: CurrentUser,
    private val shouldShowcaseRecents: ShouldShowcaseRecents,
    private val vpnStatusProviderUI: VpnStatusProviderUI,
    private val translator: Translator
) {
    fun getViewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        connect: VpnConnect = VpnConnect { _, _, _ -> },
    ) = SearchViewModel(
        savedStateHandle,
        dataAdapter = adapter,
        searchDataAdapter = searchAdapter,
        connect = connect,
        shouldShowcaseRecents = shouldShowcaseRecents,
        currentUser = currentUser,
        vpnStatusProviderUI = vpnStatusProviderUI,
        translator = translator,
    )
}
