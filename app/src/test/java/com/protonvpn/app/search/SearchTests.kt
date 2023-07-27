/*
 * Copyright (c) 2022 Proton AG
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
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.vpn.SERVER_FEATURE_RESTRICTED
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.search.Search
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.test.shared.MockSharedPreference
import com.protonvpn.test.shared.MockedServers
import com.protonvpn.test.shared.createGetSmartProtocols
import com.protonvpn.test.shared.createInMemoryServersStore
import com.protonvpn.test.shared.createServer
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.mockkObject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchTests {

    @get:Rule
    val instantTaskExecutor = InstantTaskExecutorRule()

    @RelaxedMockK
    private lateinit var mockUserData: UserData

    @RelaxedMockK
    private lateinit var mockCurrentUser: CurrentUser

    private lateinit var search: Search

    private val gatewayServer = createServer("gateway", "XX#1", features = SERVER_FEATURE_RESTRICTED)
    private val testServers = MockedServers.serverList + gatewayServer

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Storage.setPreferences(MockSharedPreference())
        mockkObject(CountryTools)
        every { CountryTools.getPreferredLocale() } returns Locale.US

        val supportsProtocol = SupportsProtocol(createGetSmartProtocols())
        val serverManager = ServerManager(
            mockUserData,
            mockCurrentUser,
            { 0 },
            supportsProtocol,
            createInMemoryServersStore(),
            mockk(relaxed = true)
        )
        serverManager.setServers(testServers, Locale.getDefault().language)
        search = Search(serverManager)
    }

    @Test
    fun emptySearch() {
        assertTrue(search("", false).isEmpty)
    }

    @Test
    fun citySearch() {
        val match = search("sto", false).cities.first()
        assertEquals("Stockholm", match.text)
        assertEquals(0, match.index)

        val matchNY = search("york", false).cities.first()
        assertEquals("New York City", matchNY.text)
        assertEquals(4, matchNY.index)

        val matchTranslationNoAccent = search("paryz", false).cities.first()
        assertEquals("Paryż", matchTranslationNoAccent.text)
    }

    @Test
    fun regionSearch() {
        assertEquals("Toronto", search("ontario", false).cities.first().value.first().city)
    }

    @Test
    fun countrySearch() {
        assertEquals(listOf(CountryTools.getFullName("FI")), search("fin", false).countries.map { it.text })
    }

    @Test
    fun secureCoreServerSearch() {
        assertTrue(search("FI", true).servers.any { it.value.serverName == "CH-FI#1" })
    }

    @Test
    fun gatewayServerSearch() {
        assertEquals(listOf(gatewayServer), search("XX#1", false).servers.map { it.value })
    }
}
