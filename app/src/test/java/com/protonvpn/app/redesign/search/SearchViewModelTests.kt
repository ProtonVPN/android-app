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
import app.cash.turbine.test
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.redesign.countries.Translator
import com.protonvpn.android.redesign.countries.ui.ServerGroupUiItem
import com.protonvpn.android.redesign.countries.ui.ServerListViewModelDataAdapter
import com.protonvpn.android.redesign.main_screen.ui.ShouldShowcaseRecents
import com.protonvpn.android.redesign.search.FetchServerByName
import com.protonvpn.android.redesign.search.FetchServerResult
import com.protonvpn.android.redesign.search.SearchServerRemote
import com.protonvpn.android.redesign.search.TextMatch
import com.protonvpn.android.redesign.search.ui.SearchViewModel
import com.protonvpn.android.redesign.search.ui.SearchViewModelDataAdapter
import com.protonvpn.android.redesign.search.ui.SearchViewState
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.VpnConnect
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.android.vpn.usecases.FakeServerListTruncationEnabled
import com.protonvpn.android.vpn.usecases.GetTruncationMustHaveIDs
import com.protonvpn.android.vpn.usecases.ServerListTruncationEnabled
import com.protonvpn.android.vpn.usecases.TransientMustHaves
import com.protonvpn.app.testRules.RobolectricHiltAndroidRule
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createServer
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.ArrayDeque
import java.util.Locale
import javax.inject.Inject
import kotlin.test.Test
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
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
    lateinit var getMustHaveServers: GetTruncationMustHaveIDs

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
                createServer(exitCountry = "US", city = "Portland", serverName = "US#1"),
                createServer(exitCountry = "PT", city = "Porto", serverName = "PO#1"),
                createServer(exitCountry = "PL", city = "Warsaw", serverName = "PL#1"),
            ),
            null,
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
                createServer(exitCountry = "EN", city = "York"),
                createServer(exitCountry = "US", city = "New York"),
            ),
            null,
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
            listOf(createServer(exitCountry = "US", city = "Portland", serverName = "US-CA#10")),
            null,
        )
        viewModel.localeFlow.value = Locale.US

        viewModel.stateFlow.test {
            viewModel.setQuery("us-ca#1")
            var state = assertIs<SearchViewState.Result>(expectMostRecentItem())
            state.assertSearchResult(
                expectedServers = listOf("US-CA#10")
            )

            viewModel.setQuery("us-ca1")
            // Assert that the highlight span accounts for the #.
            assertTextMatch(
                TextMatch(0, 7, "US-CA#10"),
                assertIs<SearchViewState.Result>(expectMostRecentItem())
            )

            viewModel.setQuery("ca#1")
            assertTextMatch(
                TextMatch(3, 4, "US-CA#10"),
                assertIs<SearchViewState.Result>(expectMostRecentItem())
            )

            viewModel.setQuery("ca1")
            // Assert that the highlight span accounts for the #.
            assertTextMatch(
                TextMatch(3, 4, "US-CA#10"),
                assertIs<SearchViewState.Result>(expectMostRecentItem())
            )
        }
    }

    @Test
    fun `remote server search - fetch server`() = runTest {
        val ch2 = createServer(serverId = "id2", exitCountry = "CH", serverName = "CH#2")
        val fetchServerByName: FetchServerByName = mockk()
        coEvery { fetchServerByName.invoke("CH#2") } returns FetchServerResult.Success(ch2)

        viewModel = searchViewModelInjector.getViewModel(
            fetchServerByName = fetchServerByName,
            serverListTruncationEnabled = FakeServerListTruncationEnabled(true)
        )

        serverManager.setServers(
            listOf(createServer(serverId = "id1", exitCountry = "CH", serverName = "CH#1")),
            null,
        )
        viewModel.localeFlow.value = Locale.US

        viewModel.stateFlow.test {
            viewModel.setQuery("CH#2")
            val state1 = expectMostRecentItem()
            assertIs<SearchViewState.Result>(state1)
            assertTrue(state1.result.items.isEmpty())

            advanceTimeBy(5_100)
            val state2 = expectMostRecentItem()
            assertIs<SearchViewState.Result>(state2)
            state2.assertSearchResult(
                expectedServers = listOf("CH#2")
            )
        }

        assertEquals(listOf("CH#1", "CH#2"), serverManager.allServers.map { it.serverName })
        assertTrue(getMustHaveServers().contains("id2"))
    }

    @Test
    fun `remote server search - don't search when available locally`() = runTest {
        val ch2 = createServer(serverId = "2", exitCountry = "CH", serverName = "CH#2", city = "Zurich")
        val fetchServerByName: FetchServerByName = mockk()
        coEvery { fetchServerByName.invoke("CH#2") } returns FetchServerResult.Success(ch2)
        viewModel.localeFlow.value = Locale.US
        serverManager.setServers(listOf(ch2), null)

        viewModel.stateFlow.test {
            viewModel.setQuery("CH#2")
            advanceTimeBy(5_100)
            val state = expectMostRecentItem()
            assertIs<SearchViewState.Result>(state)
            state.assertSearchResult(
                expectedServers = listOf("CH#2")
            )
            coVerify(exactly = 0) { fetchServerByName.invoke(any()) }
        }
    }

    @Test
    fun `remote server search - don't search for regular queries`() = runTest {
        val ch2 = createServer(serverId = "2", exitCountry = "CH", serverName = "CH#2", city = "Zurich")
        val fetchServerByName: FetchServerByName = mockk()
        coEvery { fetchServerByName.invoke("CH#2") } returns FetchServerResult.Success(ch2)
        viewModel.localeFlow.value = Locale.US

        viewModel.setQuery("Zurich")

        advanceTimeBy(5_100)
        val state = viewModel.stateFlow.filterIsInstance<SearchViewState.Result>().first()
        assertTrue(state.result.items.isEmpty())
        coVerify(exactly = 0) { fetchServerByName.invoke(any()) }
    }

    @Test
    fun `remote server search - disabled after a 429`() = runTest {
        val fetchServerByName: FetchServerByName = mockk()
        coEvery { fetchServerByName.invoke(any()) } returns FetchServerResult.TryLater
        viewModel = searchViewModelInjector.getViewModel(
            fetchServerByName = fetchServerByName,
            serverListTruncationEnabled = FakeServerListTruncationEnabled(true)
        )

        viewModel.setQuery("CH#1")
        advanceTimeBy(5_100)
        coVerify(exactly = 1) { fetchServerByName.invoke(any()) }

        viewModel.setQuery("CH#2")
        advanceTimeBy(5_100)
        coVerify(exactly = 1) { fetchServerByName.invoke(any()) }
    }

    @Test
    fun `remote server search - disabled when FF is off`() = runTest {
        val fetchServerByName: FetchServerByName = mockk()
        coEvery { fetchServerByName.invoke(any()) } returns FetchServerResult.None

        viewModel = searchViewModelInjector.getViewModel(
            fetchServerByName = fetchServerByName,
            serverListTruncationEnabled = FakeServerListTruncationEnabled(false)
        )
        viewModel.setQuery("CH#1")
        advanceTimeBy(5_100)

        viewModel.setQuery("Invalid")
        advanceTimeBy(5_100)
        coVerify(exactly = 0) { fetchServerByName.invoke(any()) }
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
    private val translator: Translator,
    private val serverManager: ServerManager2,
    private val transientMustHaves: TransientMustHaves,
) {
    fun getViewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        connect: VpnConnect = VpnConnect { _, _, _ -> },
        fetchServerByName: FetchServerByName = mockk(relaxed = true),
        serverListTruncationEnabled: ServerListTruncationEnabled = FakeServerListTruncationEnabled(false),
    ) = SearchViewModel(
        savedStateHandle,
        dataAdapter = adapter,
        searchDataAdapter = searchAdapter,
        connect = connect,
        shouldShowcaseRecents = shouldShowcaseRecents,
        currentUser = currentUser,
        vpnStatusProviderUI = vpnStatusProviderUI,
        translator = translator,
        remoteSearch = SearchServerRemote(
            serverListTruncationEnabled,
            fetchServerByName,
            transientMustHaves,
            serverManager,
        )
    )
}
