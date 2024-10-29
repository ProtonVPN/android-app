/*
 * Copyright (c) 2024 Proton AG
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

package com.protonvpn.app.vpn

import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.vpn.ConnectingDomain
import com.protonvpn.android.models.vpn.SERVER_FEATURE_P2P
import com.protonvpn.android.models.vpn.ServerEntryInfo
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.recents.data.SettingsOverrides
import com.protonvpn.android.redesign.recents.data.toData
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.GetOnlineServersForIntent
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.test.shared.MockSharedPreference
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestDispatcherProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createGetSmartProtocols
import com.protonvpn.test.shared.createServer
import io.mockk.MockKAnnotations
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GetOnlineServersForIntentTests {

    private lateinit var userSettingsFlow: MutableStateFlow<LocalUserSettings>
    private lateinit var testScope: TestScope
    private lateinit var getOnlineServersForIntent: GetOnlineServersForIntent
    private lateinit var serverManager: ServerManager2

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Storage.setPreferences(MockSharedPreference())

        val testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)
        userSettingsFlow = MutableStateFlow(LocalUserSettings.Default)

        val servers = listOf(
            createServer(serverName = "SE#1", exitCountry = "SE", isOnline = false),
            createServer(serverId = "SE#2_id", serverName = "SE#2", exitCountry = "SE", score = 2.0),
            createServer(serverName = "SE#3", exitCountry = "SE", score = 1.0),
            createServer(serverName = "SE#4", exitCountry = "SE", entryCountry = "CH", isSecureCore = true),
            createServer(serverName = "SE#4", exitCountry = "SE", tier = 1),
            createServer(serverName = "CH#1", exitCountry = "CH", city = "Zurich"),
            createServer(serverName = "CH#2", exitCountry = "CH", city = "Zurich", features = SERVER_FEATURE_P2P),
            createServer(serverName = "CH-GT#1", exitCountry = "CH", gatewayName = "GT", city = "Zurich"),
            createServer(serverName = "CH-GT#2", exitCountry = "CH", gatewayName = "GT", city = "Zurich"),
            createServer(serverName = "CH-GT#3", exitCountry = "CH", gatewayName = "GT", city = "Zurich", connectingDomains = listOf(
                ConnectingDomain(
                    entryIp = null,
                    entryIpPerProtocol = mapOf(ProtocolSelection.STEALTH.apiName to ServerEntryInfo("1.2.3.4")),
                    entryDomain = "dummy.protonvpn.net",
                    exitIp = "2.3.4.5",
                    id = "id",
                    label = null,
                    isOnline = true,
                    publicKeyX25519 = "dummyKey"
                )
            )),
        )

        val supportsProtocol = SupportsProtocol(createGetSmartProtocols())
        serverManager = createInMemoryServerManager(
            testScope,
            TestDispatcherProvider(testDispatcher),
            supportsProtocol,
            CurrentUser(TestCurrentUserProvider(TestUser.freeUser.vpnUser)),
            servers
        )
        getOnlineServersForIntent = GetOnlineServersForIntent(serverManager, supportsProtocol)
    }

    @Test
    fun fastestInCountryMatchesTierAndOnlineSortedByScore() = testScope.runTest {
        val profileIntent = ConnectIntent.FastestInCountry(CountryId("SE"), emptySet())
        val result = getOnlineServersForIntent(profileIntent, userSettingsFlow.value, VpnUser.FREE_TIER)
        assertEquals(listOf("SE#3", "SE#2"), result.map { it.serverName })
    }

    @Test
    fun fastestInCityWithFeatures() = testScope.runTest {
        val profileIntent = ConnectIntent.FastestInCity(CountryId("CH"), "Zurich", setOf(ServerFeature.P2P))
        val result = getOnlineServersForIntent(profileIntent, userSettingsFlow.value, VpnUser.FREE_TIER)
        assertEquals(listOf("CH#2"), result.map { it.serverName })
    }

    @Test
    fun gatewayWithProtocolOverride() = testScope.runTest {
        val overrides = SettingsOverrides(ProtocolSelection(VpnProtocol.WireGuard).toData(), null, null, null)
        val profileIntent = ConnectIntent.Gateway("GT", null, settingsOverrides = overrides)
        val result = getOnlineServersForIntent(profileIntent, userSettingsFlow.value, VpnUser.FREE_TIER)
        assertEquals(listOf("CH-GT#1", "CH-GT#2"), result.map { it.serverName })
    }

    @Test
    fun secureCore() = testScope.runTest {
        val profileIntent = ConnectIntent.SecureCore(CountryId("SE"), CountryId.fastest)
        val result = getOnlineServersForIntent(profileIntent, userSettingsFlow.value, VpnUser.FREE_TIER)
        assertEquals(listOf("SE#4"), result.map { it.serverName })
    }

    @Test
    fun server() = testScope.runTest {
        val profileIntent = ConnectIntent.Server("SE#2_id", CountryId("SE"), emptySet())
        val result = getOnlineServersForIntent(profileIntent, userSettingsFlow.value, VpnUser.FREE_TIER)
        assertEquals(listOf("SE#2"), result.map { it.serverName })
    }
}