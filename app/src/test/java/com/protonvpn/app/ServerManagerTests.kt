/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.app

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.servers.api.ConnectingDomain
import com.protonvpn.android.servers.api.LoadUpdate
import com.protonvpn.android.servers.api.SERVER_FEATURE_P2P
import com.protonvpn.android.servers.api.SERVER_FEATURE_RESTRICTED
import com.protonvpn.android.servers.api.SERVER_FEATURE_SECURE_CORE
import com.protonvpn.android.servers.api.SERVER_FEATURE_TOR
import com.protonvpn.android.servers.Server
import com.protonvpn.android.servers.api.ServerEntryInfo
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.mocks.createInMemoryServerManager
import com.protonvpn.test.shared.MockSharedPreference
import com.protonvpn.test.shared.TestDispatcherProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createGetSmartProtocols
import com.protonvpn.test.shared.createServer
import com.protonvpn.test.shared.mockVpnUser
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import me.proton.core.util.kotlin.deserialize
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.EnumSet
import java.util.Locale
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class ServerManagerTests {

    private lateinit var manager: ServerManager
    private lateinit var serverManager2: ServerManager2

    @RelaxedMockK private lateinit var currentUser: CurrentUser

    private lateinit var currentSettings: MutableStateFlow<LocalUserSettings>
    private lateinit var testDispatcherProvider: TestDispatcherProvider
    private lateinit var testScope: TestScope

    private val plusUser = TestUser.plusUser.vpnUser
    private val freeUser = TestUser.freeUser.vpnUser

    private val gatewayServer = createServer(
        "dedicated",
        serverName = "CA Gateway#1",
        exitCountry = "CA",
        gatewayName = "Gateway 1",
        features = SERVER_FEATURE_RESTRICTED
    )
    private lateinit var regularServers: List<Server>

    @get:Rule
    var rule = InstantTaskExecutorRule()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Storage.setPreferences(MockSharedPreference())
        mockkObject(CountryTools)
        currentUser.mockVpnUser { plusUser }
        every { CountryTools.getPreferredLocale() } returns Locale.US

        val dispatcher = UnconfinedTestDispatcher()
        testDispatcherProvider = TestDispatcherProvider(dispatcher)
        testScope = TestScope(dispatcher)
        currentSettings = MutableStateFlow(LocalUserSettings.Default)

        val serversFile = File(javaClass.getResource("/Servers.json")?.path)
        regularServers = serversFile.readText().deserialize(ListSerializer(Server.serializer()))

        // Note: use createServerManagers in each test.
    }

    @Test
    fun doNotChooseOfflineServerFromCountry() = testScope.runTest {
        createServerManagers()
        val country = manager.getVpnExitCountry("CA", false)
        val protocol = currentSettings.value.protocol
        val countryBestServer = manager.getBestScoreServer(country!!.serverList, currentUser.vpnUser(), protocol)
        assertEquals("CA#2", countryBestServer!!.serverName)
    }

    @Test
    fun doNotChooseOfflineServerFromAll() = testScope.runTest {
        createServerManagers()
        val protocol = currentSettings.value.protocol
        val countryServers = manager.allServersByScore.filter { !it.isGatewayServer }
        val server = manager.getBestScoreServer(countryServers, currentUser.vpnUser(), protocol)
        assertNotNull(server)
        assertEquals("DE#1", server.serverName)
    }

    @Test
    fun testOnlineAccessibleServersSeparatesGatewaysFromRegular() = testScope.runTest {
        createServerManagers()
        val gatewayName = gatewayServer.gatewayName
        val gatewayServers = serverManager2.getOnlineAccessibleServers(false, gatewayName, plusUser, ProtocolSelection.SMART)
        val regularServers = serverManager2.getOnlineAccessibleServers(false, null, plusUser, ProtocolSelection.SMART)

        assertEquals(listOf(gatewayServer), gatewayServers)
        assertFalse(regularServers.contains(gatewayServer))
    }

    @Test
    fun testGetServerById() {
        testScope.createServerManagers()
        val server = manager.getServerById(
            "1H8EGg3J1QpSDL6K8hGsTvwmHXdtQvnxplUMePE7Hruen5JsRXvaQ75-sXptu03f0TCO-he3ymk0uhrHx6nnGQ=="
        )
        Assert.assertNotNull(server)
        Assert.assertEquals("CA#2", server?.serverName)
    }

    @Test
    fun testGetBestServerForConnectIntentWithFeatures() = testScope.runTest {
        fun createSeattleServer(serverId: String, score: Double, features: Int, entryCountry: String? = null) =
            createServer(
                serverId,
                exitCountry = "US",
                entryCountry = entryCountry ?: "US",
                state = "Washington",
                city = "Seattle",
                score = score,
                features = features
            )

        suspend fun testIntent(expectedServerId: String, connectIntent: ConnectIntent) {
            val protocol = currentSettings.value.protocol
            val server = serverManager2.getBestServerForConnectIntent(connectIntent, plusUser, protocol)
            assertEquals(expectedServerId, server?.serverId)
        }

        val servers = listOf(
            // The best server has no features.
            createSeattleServer("1", score = .1, features = 0),
            createSeattleServer("2", score = 0.5, features = SERVER_FEATURE_P2P),
            createSeattleServer("3", score = 0.5, features = SERVER_FEATURE_TOR),
            createSeattleServer("4", score = 1.0, features = SERVER_FEATURE_TOR or SERVER_FEATURE_P2P),
            createSeattleServer("SC", score = .1, features = SERVER_FEATURE_SECURE_CORE, entryCountry = "CH")
        )
        createServerManagers()
        manager.setServers(servers, statusId = null, language = null)

        testIntent("2", ConnectIntent.FastestInCountry(CountryId.fastest, EnumSet.of(ServerFeature.P2P)))
        testIntent(
            "4",
            ConnectIntent.FastestInCountry(CountryId.fastest, EnumSet.of(ServerFeature.P2P, ServerFeature.Tor))
        )
        testIntent("2", ConnectIntent.FastestInCountry(CountryId("US"), EnumSet.of(ServerFeature.P2P)))
        testIntent("3", ConnectIntent.FastestInCountry(CountryId("US"), EnumSet.of(ServerFeature.Tor)))
        testIntent("2", ConnectIntent.FastestInState(CountryId("US"), "Washington", EnumSet.of(ServerFeature.P2P)))
        testIntent("2", ConnectIntent.FastestInCity(CountryId("US"), "Seattle", EnumSet.of(ServerFeature.P2P)))

        // Secure Core is a bit special but also uses features, make sure it's not filtered out.
        testIntent("SC", ConnectIntent.SecureCore(CountryId("US"), entryCountry = CountryId("CH")))
        testIntent("SC", ConnectIntent.SecureCore(CountryId("US"), entryCountry = CountryId.fastest))
    }

    @Test
    fun testGetBestServerForConnectIntentWithUnavailableServers() = testScope.runTest {
        suspend fun testIntent(expectedServerId: String?, connectIntent: ConnectIntent, vpnUser: VpnUser) {
            val protocol = currentSettings.value.protocol
            val server = serverManager2.getBestServerForConnectIntent(connectIntent, vpnUser, protocol)
            assertEquals(expectedServerId, server?.serverId)
        }

        val servers = listOf(
            createServer("US plus offline", score = 1.0, exitCountry = "US", isOnline = false, tier = 2),
            createServer("US plus online", score = 2.0, exitCountry = "US", tier = 2),
            createServer("US plus online second", score = 3.0, exitCountry = "US", tier = 2),
            createServer("US plus offline", score = 3.0, exitCountry = "US", isOnline = false, tier = 2),
            createServer("PL plus offline", score = 3.0, exitCountry = "PL", isOnline = false, tier = 2),
            createServer("PL free offline", score = 4.0, exitCountry = "PL", isOnline = false, tier = 0),
            createServer("CH free online", score = 5.0, exitCountry = "CH", tier = 0),
            createServer("PL SC plus online", score = 1.0, exitCountry = "PL", tier = 2, isSecureCore = true),
            createServer("US SC plus online", score = 3.0, exitCountry = "US", tier = 2, isSecureCore = true),
        )
        createServerManagers()
        manager.setServers(servers, null, null)

        val fastestPl = ConnectIntent.FastestInCountry(CountryId("PL"), emptySet())
        val fastestCh = ConnectIntent.FastestInCountry(CountryId("CH"), emptySet())
        val secureCorePl = ConnectIntent.SecureCore(CountryId("PL"), CountryId.fastest)
        val secureCoreUs = ConnectIntent.SecureCore(CountryId("US"), CountryId.fastest)
        testIntent("US plus online", ConnectIntent.Fastest, plusUser)
        testIntent("CH free online", ConnectIntent.Fastest, freeUser)
        testIntent("CH free online", fastestCh, plusUser)
        testIntent("PL SC plus online", secureCorePl, plusUser)
        testIntent("US SC plus online", secureCoreUs, plusUser)
        // Offline servers are returned if no other server satisfies the intent.
        testIntent("PL plus offline", fastestPl, plusUser)
        testIntent("PL free offline", fastestPl, freeUser)
    }

    @Test
    fun updatedLoadsAreReflectedInGroupedServers() = testScope.runTest {
        val server1 = createServer("server1", exitCountry = "PL", loadPercent = 50f, score = 1.5, isOnline = true)
        val server2 = createServer("server2", exitCountry = "PL", loadPercent = 10f, score = 1.0, isOnline = false)
        val newLoads = listOf(
            LoadUpdate("server1", load = 100f, score = 0.0, status = 1),
            LoadUpdate("server2", load = 25f, score = 5.0, status = 1),
        )
        createServerManagers(servers = listOf(server1, server2))
        manager.updateLoads(newLoads)

        val country = serverManager2.getVpnExitCountry("PL", secureCoreCountry = false)
        val expectedServers = setOf(
            server1.copy(load = 100f, score = 0.0),
            server2.copy(load = 25f, score = 5.0, isOnline = true)
        )
        assertEquals(expectedServers, country?.serverList?.toSet())
    }

    @Test
    fun getRandomServerIgnoresServersWithUnsupportedProtocols() = testScope.runTest {
        val protocols = mapOf("OpenVPNTCP" to ServerEntryInfo(ipv4 = "1.2.3.4"))
        val connectingDomain =
            ConnectingDomain(entryIpPerProtocol = protocols, entryDomain = "dummyDomain", id = "dummyId")
        val server = createServer("server1", connectingDomains = listOf(connectingDomain))
        createServerManagers(
            servers = listOf(server),
            supportedSmartProtocols = listOf(ProtocolSelection(VpnProtocol.WireGuard))
        )

        val serverWithUnsupportedProtocol = serverManager2.getRandomServer(freeUser, ProtocolSelection.SMART)
        assertNull(serverWithUnsupportedProtocol)
    }

    private fun TestScope.createServerManagers(
        servers: List<Server> = regularServers + gatewayServer,
        supportedSmartProtocols: List<ProtocolSelection> = ProtocolSelection.REAL_PROTOCOLS
    ) {
        val supportsProtocol = SupportsProtocol(createGetSmartProtocols(supportedSmartProtocols))
        manager = createInMemoryServerManager(
            this,
            testDispatcherProvider,
            supportsProtocol,
            servers,
        )
        serverManager2 = ServerManager2(manager, supportsProtocol)
    }

}
