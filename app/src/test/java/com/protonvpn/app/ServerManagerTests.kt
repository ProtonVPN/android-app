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
import com.protonvpn.android.models.profiles.SavedProfilesV3
import com.protonvpn.android.models.vpn.SERVER_FEATURE_P2P
import com.protonvpn.android.models.vpn.SERVER_FEATURE_RESTRICTED
import com.protonvpn.android.models.vpn.SERVER_FEATURE_SECURE_CORE
import com.protonvpn.android.models.vpn.SERVER_FEATURE_TOR
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettingsCached
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.userstorage.ProfileManager
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.test.shared.MockSharedPreference
import com.protonvpn.test.shared.createGetSmartProtocols
import com.protonvpn.test.shared.createInMemoryServersStore
import com.protonvpn.test.shared.createServer
import com.protonvpn.test.shared.mockVpnUser
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import me.proton.core.util.kotlin.deserialize
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    @RelaxedMockK private lateinit var vpnUser: VpnUser

    private lateinit var currentSettings: MutableStateFlow<LocalUserSettings>
    private lateinit var profileManager: ProfileManager
    private lateinit var testScope: TestScope

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
        currentUser.mockVpnUser { vpnUser }
        every { vpnUser.userTier } returns 2
        every { CountryTools.getPreferredLocale() } returns Locale.US

        testScope = TestScope(UnconfinedTestDispatcher())
        val bgScope = testScope.backgroundScope

        currentSettings = MutableStateFlow(LocalUserSettings.Default)
        val currentUserSettings = EffectiveCurrentUserSettingsCached(currentSettings)

        val supportsProtocol = SupportsProtocol(createGetSmartProtocols())
        profileManager = ProfileManager(SavedProfilesV3.defaultProfiles(), bgScope, currentUserSettings, mockk())
        manager = ServerManager(bgScope, currentUserSettings, currentUser, { 0L }, supportsProtocol, createInMemoryServersStore(), profileManager)
        val serversFile = File(javaClass.getResource("/Servers.json")?.path)
        regularServers = serversFile.readText().deserialize(ListSerializer(Server.serializer()))

        val allServers = regularServers + gatewayServer
        runBlocking {
            manager.setServers(allServers, null)
        }
        serverManager2 =
            ServerManager2(manager, EffectiveCurrentUserSettings(bgScope, currentSettings), supportsProtocol)
    }

    @Test
    fun doNotChooseOfflineServerFromCountry() {
        val country = manager.getVpnExitCountry("CA", false)
        val countryBestServer = manager.getBestScoreServer(country!!.serverList)
        Assert.assertEquals("CA#2", countryBestServer!!.serverName)
    }

    @Test
    fun doNotChooseOfflineServerFromAll() = testScope.runTest {
        val server = manager.getBestScoreServer(false, serverFeatures = emptySet(), currentUser.vpnUser())
        assertNotNull(server)
        assertEquals("DE#1", server.serverName)
    }

    @Test
    fun testOnlineAccessibleServersSeparatesGatewaysFromRegular() = testScope.runTest {
        val gatewayName = gatewayServer.gatewayName
        val gatewayServers = serverManager2.getOnlineAccessibleServers(false, gatewayName, vpnUser, ProtocolSelection.SMART)
        val regularServers = serverManager2.getOnlineAccessibleServers(false, null, vpnUser, ProtocolSelection.SMART)

        assertEquals(listOf(gatewayServer), gatewayServers)
        assertFalse(regularServers.contains(gatewayServer))
    }

    @Test
    fun testGetServerById() {
        val server = manager.getServerById(
            "1H8EGg3J1QpSDL6K8hGsTvwmHXdtQvnxplUMePE7Hruen5JsRXvaQ75-sXptu03f0TCO-he3ymk0uhrHx6nnGQ=="
        )
        Assert.assertNotNull(server)
        Assert.assertEquals("CA#2", server?.serverName)
    }

    @Test
    fun testGetBestServerWithFeatures() = testScope.runTest {
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
            val server = serverManager2.getServerForConnectIntent(connectIntent, vpnUser)
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
        manager.setServers(servers, null)

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
}
