/*
 * Copyright (c) 2025. Proton AG
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

package com.protonvpn.app.redesign.recents.usecases

import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.recents.usecases.GetIntentAvailability
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentAvailability
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.servers.api.SERVER_FEATURE_P2P
import com.protonvpn.android.servers.api.SERVER_FEATURE_RESTRICTED
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.mocks.createInMemoryServerManager
import com.protonvpn.test.shared.MockSharedPreference
import com.protonvpn.test.shared.TestDispatcherProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createGetSmartProtocols
import com.protonvpn.test.shared.createServer
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GetIntentAvailabilityTests {

    private lateinit var serverManager: ServerManager
    private lateinit var testScope: TestScope

    private lateinit var getIntentAvailability: GetIntentAvailability

    private val serverFreeCh = createServer(exitCountry = "CH", tier = 0)
    private val serverCh = createServer(exitCountry = "CH", tier = 2)
    private val serverLtOffline = createServer(exitCountry = "LT", tier = 2, isOnline = false)
    private val serverPlP2P = createServer(exitCountry = "PL", features = SERVER_FEATURE_P2P, tier = 2)
    private val serverSecureCore = createServer(exitCountry = "US", entryCountry = "CH", isSecureCore = true, tier = 2)
    private val serverGateway =
        createServer(exitCountry = "CH", gatewayName = "Gateway", features = SERVER_FEATURE_RESTRICTED, tier = 2)

    private val userFree = TestUser.freeUser.vpnUser
    private val userPlus = TestUser.plusUser.vpnUser

    @Before
    fun setup() {
        Storage.setPreferences(MockSharedPreference())
        val testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)

        val supportsProtocol = SupportsProtocol(createGetSmartProtocols())

        serverManager = createInMemoryServerManager(
            testScope,
            TestDispatcherProvider(testDispatcher),
            supportsProtocol = supportsProtocol,
            initialServers = emptyList(),
        )
        val serverManager2 = ServerManager2(serverManager, supportsProtocol)

        getIntentAvailability = GetIntentAvailability(serverManager2, supportsProtocol)
    }

    @Test
    fun `all servers - paid`() = testScope.runTest {
        serverManager.setServers(
            listOf(serverFreeCh, serverCh, serverLtOffline, serverPlP2P, serverSecureCore, serverGateway),
            statusId = "1",
            language = null
        )

        val cases = listOf(
            ConnectIntent.FastestInCountry(CountryId.fastest, emptySet()) to ConnectIntentAvailability.ONLINE,
            ConnectIntent.FastestInCountry(CountryId("LT"), emptySet()) to ConnectIntentAvailability.AVAILABLE_OFFLINE,
            ConnectIntent.FastestInCountry(CountryId("DE"), emptySet()) to ConnectIntentAvailability.NO_SERVERS,
            ConnectIntent.FastestInCountry(CountryId.fastest, setOf(ServerFeature.Tor))
                to ConnectIntentAvailability.NO_SERVERS,
            ConnectIntent.Server("Nonexistent", CountryId("CH"), emptySet())
                to ConnectIntentAvailability.NO_SERVERS,
        )
        runAvailabilityTestCases(cases, userPlus)
    }

    @Test
    fun `free user`() = testScope.runTest {
        serverManager.setServers(
            listOf(serverFreeCh, serverCh, serverLtOffline, serverPlP2P, serverSecureCore, serverGateway),
            statusId = "1",
            language = null
        )

        val cases = listOf(
            ConnectIntent.FastestInCountry(CountryId.fastest, emptySet()) to ConnectIntentAvailability.ONLINE,
            ConnectIntent.FastestInCountry(CountryId("CH"), emptySet()) to ConnectIntentAvailability.ONLINE,
            ConnectIntent.FastestInCountry(CountryId("PL"), emptySet()) to ConnectIntentAvailability.UNAVAILABLE_PLAN,
            ConnectIntent.FastestInCountry(CountryId("LT"), emptySet()) to ConnectIntentAvailability.UNAVAILABLE_PLAN,
            ConnectIntent.SecureCore(CountryId("US"), entryCountry = CountryId.fastest)
                to ConnectIntentAvailability.UNAVAILABLE_PLAN,
            ConnectIntent.fromServer(serverFreeCh, emptySet()) to ConnectIntentAvailability.ONLINE,
        )
        runAvailabilityTestCases(cases, userFree)
    }

    @Test
    fun `cities - paid`() = testScope.runTest {
        val newYorkOnline = createServer(exitCountry = "US", city = "New York")
        val newYorkOffline = createServer(exitCountry = "US", city = "New York", isOnline = false)
        val seattleP2POffline = createServer(exitCountry = "US", city = "Seattle", features = SERVER_FEATURE_P2P, isOnline = false)
        val seattle = createServer(exitCountry = "US", city = "Seattle")

        serverManager.setServers(listOf(newYorkOnline, newYorkOffline, seattleP2POffline, seattle), "1", null)

        val cases = listOf(
            ConnectIntent.FastestInCountry(CountryId("US"), emptySet()) to ConnectIntentAvailability.ONLINE,
            ConnectIntent.FastestInCity(CountryId("US"), cityEn = "New York", emptySet())
                to ConnectIntentAvailability.ONLINE,
            ConnectIntent.FastestInCity(CountryId("US"), cityEn = "Seattle", emptySet())
                to ConnectIntentAvailability.ONLINE,
            ConnectIntent.FastestInCity(CountryId("US"), cityEn = "Seattle", setOf(ServerFeature.P2P))
                to ConnectIntentAvailability.AVAILABLE_OFFLINE,
            ConnectIntent.FastestInCity(CountryId("US"), cityEn = "Denver", emptySet())
                to ConnectIntentAvailability.NO_SERVERS,
        )
        runAvailabilityTestCases(cases, userPlus)
    }

    @Test
    fun `gateways - paid`() = testScope.runTest {
        val gatewayA1 = createServer("A1", exitCountry = "CH", gatewayName = "A", features = SERVER_FEATURE_RESTRICTED, isOnline = false)
        val gatewayB1 = createServer("B1", exitCountry = "CH", gatewayName = "B", features = SERVER_FEATURE_RESTRICTED)
        val gatewayB2 = createServer("B2", exitCountry = "CH", gatewayName = "B", features = SERVER_FEATURE_RESTRICTED, isOnline = false)
        serverManager.setServers(listOf(gatewayA1, gatewayB1, gatewayB2), "1", null)

        val cases = listOf(
            ConnectIntent.FastestInCountry(CountryId.fastest, emptySet()) to ConnectIntentAvailability.NO_SERVERS,
            ConnectIntent.Gateway("A", null) to ConnectIntentAvailability.AVAILABLE_OFFLINE,
            ConnectIntent.Gateway("B", null) to ConnectIntentAvailability.ONLINE,
            ConnectIntent.Gateway("B", serverId = "B2") to ConnectIntentAvailability.AVAILABLE_OFFLINE,
        )
        runAvailabilityTestCases(cases, userPlus)
    }

    private suspend fun runAvailabilityTestCases(casesList: List<Pair<ConnectIntent, ConnectIntentAvailability>>, vpnUser: VpnUser) {
        casesList.forEachIndexed { index, (intent, expectedAvailability) ->
            val result = getIntentAvailability(intent, vpnUser, ProtocolSelection.SMART)
            assertEquals("Case $index", expectedAvailability, result)
        }
    }
}
