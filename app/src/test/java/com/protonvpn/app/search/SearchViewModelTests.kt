/*
 * Copyright (c) 2022. Proton AG
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

package com.protonvpn.app.search

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.search.Search
import com.protonvpn.android.search.SearchViewModel
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.test.shared.MockSharedPreference
import com.protonvpn.test.shared.MockedServers
import com.protonvpn.test.shared.TestUser
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import me.proton.core.test.kotlin.CoroutinesTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTests : CoroutinesTest {

    @get:Rule
    var rule = InstantTaskExecutorRule()

    private lateinit var searchViewModel: SearchViewModel

    @RelaxedMockK
    private lateinit var mockUserData: UserData
    @RelaxedMockK
    private lateinit var mockCurrentUser: CurrentUser
    @MockK
    private lateinit var mockConnectionManager: VpnConnectionManager
    @MockK
    private lateinit var mockVpnStateMonitor: VpnStateMonitor

    private lateinit var vpnStateFlow: MutableStateFlow<VpnStateMonitor.Status>
    private lateinit var vpnUserFlow: MutableStateFlow<VpnUser?>

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Storage.setPreferences(MockSharedPreference())
        mockkObject(CountryTools)
        every { CountryTools.getPreferredLocale() } returns Locale.US

        vpnStateFlow = MutableStateFlow(VpnStateMonitor.Status(VpnState.Disabled, null))
        every { mockVpnStateMonitor.status } returns vpnStateFlow

        vpnUserFlow = MutableStateFlow(TestUser.plusUser.vpnUser)
        every { mockCurrentUser.vpnUserFlow } returns vpnUserFlow

        val serverManager = ServerManager(mockUserData, mockCurrentUser)
        serverManager.setServers(MockedServers.serverList, null)
        val search = Search(serverManager)

        searchViewModel = SearchViewModel(
            SavedStateHandle(),
            mockUserData,
            mockConnectionManager,
            mockVpnStateMonitor,
            serverManager,
            search,
            mockCurrentUser
        )
    }

    @Test
    fun `when query is 'ca' matching results are returned`() = coroutinesTest {
        searchViewModel.setQuery("ca")
        val state = searchViewModel.viewState.first()

        assertIs<SearchViewModel.ViewState.SearchResults>(state)
        assertEquals(listOf("Canada"), state.countries.map { it.match.text })
        assertEquals(listOf("Canada"), state.countries.map { it.match.value.countryName })
        assertEquals(listOf("Cairo"), state.cities.map { it.match.text })
        assertEquals(listOf("CA#1", "CA#2"), state.servers.map { it.match.text })
        assertEquals(listOf("CA#1", "CA#2"), state.servers.map { it.match.value.serverName })
    }

    @Test
    fun `offline servers included in search results`() = coroutinesTest {
        searchViewModel.setQuery("se")
        val state = searchViewModel.viewState.first()

        assertIs<SearchViewModel.ViewState.SearchResults>(state)
        val se1result = state.servers.find { it.match.text == "SE#1" }
        val se3result = state.servers.find { it.match.text == "SE#3" }

        assertNotNull(se1result)
        assertNotNull(se3result)
        assertTrue(se1result.isOnline)
        assertFalse(se3result.isOnline)
    }

    @Test
    fun `when connected to CA#1 Toronto result is shown connected`() = coroutinesTest {
        val server = MockedServers.serverList.first { it.serverName == "CA#1" }
        val profile = MockedServers.getProfile(VpnProtocol.Smart, server)
        vpnStateFlow.value = VpnStateMonitor.Status(VpnState.Connected, ConnectionParams(profile, server, null, null))

        searchViewModel.setQuery("tor")
        val state = searchViewModel.viewState.first()

        assertIs<SearchViewModel.ViewState.SearchResults>(state)
        assertEquals(listOf("Toronto"), state.cities.map { it.match.text })
        assertTrue(state.cities.first().isConnected)
    }

    @Test
    fun `when connected to CA#1 Canada result is shown connected`() = coroutinesTest {
        val server = MockedServers.serverList.first { it.serverName == "CA#1" }
        val profile = MockedServers.getProfile(VpnProtocol.Smart, server)
        vpnStateFlow.value = VpnStateMonitor.Status(VpnState.Connected, ConnectionParams(profile, server, null, null))

        searchViewModel.setQuery("can")
        val state = searchViewModel.viewState.first()

        assertIs<SearchViewModel.ViewState.SearchResults>(state)
        assertEquals(listOf("Canada"), state.countries.map { it.match.text })
        assertTrue(state.countries.first().isConnected)
    }

    @Test
    fun `when city match is in second word it is shown after matches on first word`() = coroutinesTest {
        searchViewModel.setQuery("k")
        val state = searchViewModel.viewState.first()

        assertIs<SearchViewModel.ViewState.SearchResults>(state)
        assertEquals(listOf("Kyiv", "Hong Kong"), state.cities.map { it.match.text })
    }

    @Test
    fun `when country match is in second word it is shown after matches on first word`() = coroutinesTest {
        searchViewModel.setQuery("s")
        val state = searchViewModel.viewState.first()

        assertIs<SearchViewModel.ViewState.SearchResults>(state)
        assertEquals(listOf("Sweden", "Hong Kong SAR China", "United States"), state.countries.map { it.match.text })
    }

    @Test
    fun `servers sorted by number`() = coroutinesTest {
        searchViewModel.setQuery("UA")
        val state = searchViewModel.viewState.first()

        assertIs<SearchViewModel.ViewState.SearchResults>(state)
        assertEquals(listOf("UA#9", "UA#10"), state.servers.map { it.match.text })
    }

    @Test
    fun `accessible servers listed first`() = coroutinesTest {
        // UA#9 is tier 1, UA#10 is tier 0.
        vpnUserFlow.value = TestUser.freeUser.vpnUser
        searchViewModel.setQuery("UA")
        val state = searchViewModel.viewState.first()

        assertIs<SearchViewModel.ViewState.SearchResults>(state)
        assertEquals(listOf("UA#10", "UA#9"), state.servers.map { it.match.text })
    }

    @Test
    fun `when query is typed fast only the end result is added to recents`() = coroutinesTest {
        searchViewModel.setQuery("s")
        searchViewModel.setQuery("sw")
        searchViewModel.setQuery("swi")
        searchViewModel.setQuery("swis")
        searchViewModel.setQuery("swiss")
        delay(3100)
        searchViewModel.setQuery("")
        val state = searchViewModel.viewState.first()
        assertIs<SearchViewModel.ViewState.SearchHistory>(state)
        assertEquals(listOf("swiss"), state.queries)
    }

    @Test
    fun `when recents are cleared state is empty`() = coroutinesTest {
        searchViewModel.setQuery("swiss")
        delay(3100)
        searchViewModel.setQuery("")
        assertIs<SearchViewModel.ViewState.SearchHistory>(searchViewModel.viewState.first())
        searchViewModel.clearRecentHistory()
        assertIs<SearchViewModel.ViewState.Empty>(searchViewModel.viewState.first())
    }

    @Test
    fun `recents show most recent first`() = coroutinesTest {
        searchViewModel.setQuery("aaa")
        delay(3100)
        searchViewModel.setQuery("bbb")
        delay(3100)
        searchViewModel.setQuery("")

        val state = searchViewModel.viewState.first()
        assertIs<SearchViewModel.ViewState.SearchHistory>(state)
        assertEquals(listOf("bbb", "aaa"), state.queries)
    }

    @Test
    fun `when the same query is saved in recents it moves to top`() = coroutinesTest {
        searchViewModel.setQuery("aaa")
        delay(3100)
        searchViewModel.setQuery("bbb")
        delay(3100)
        searchViewModel.setQuery("aaa")
        delay(3100)
        searchViewModel.setQuery("")

        val state = searchViewModel.viewState.first()
        assertIs<SearchViewModel.ViewState.SearchHistory>(state)
        assertEquals(listOf("aaa", "bbb"), state.queries)
    }

    @Test
    fun `when a query from recents is selected it moves to top of recents`() = coroutinesTest {
        searchViewModel.setQuery("aaa")
        delay(3100)
        searchViewModel.setQuery("bbb")
        delay(3100)
        searchViewModel.setQuery("")
        searchViewModel.setQueryFromRecents("aaa")
        searchViewModel.setQuery("")

        val state = searchViewModel.viewState.first()
        assertIs<SearchViewModel.ViewState.SearchHistory>(state)
        assertEquals(listOf("aaa", "bbb"), state.queries)
    }
}
