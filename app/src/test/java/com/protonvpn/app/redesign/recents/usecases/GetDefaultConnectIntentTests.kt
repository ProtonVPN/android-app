/*
 * Copyright (c) 2025 Proton AG
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

import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.redesign.recents.usecases.GetDefaultConnectIntent
import com.protonvpn.android.redesign.recents.usecases.GetIntentAvailability
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentAvailability
import com.protonvpn.android.servers.Server
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.mocks.createInMemoryServerManager
import com.protonvpn.test.shared.MockSharedPreference
import com.protonvpn.test.shared.TestDispatcherProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createGetSmartProtocols
import com.protonvpn.test.shared.createServer
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class GetDefaultConnectIntentTests {

    @MockK
    private lateinit var mockGetIntentAvailability: GetIntentAvailability

    private lateinit var getDefaultConnectIntent: GetDefaultConnectIntent

    private lateinit var serverManager: ServerManager

    private lateinit var testDispatcher: CoroutineDispatcher

    private lateinit var testScope: TestScope

    private val vpnUsers = listOf(
        null,
        TestUser.freeUser.vpnUser,
        TestUser.plusUser.vpnUser,
    )

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        Storage.setPreferences(MockSharedPreference())

        testDispatcher = UnconfinedTestDispatcher()

        testScope = TestScope(context = testDispatcher)

        val supportsProtocol = SupportsProtocol(getSmartProtocols = createGetSmartProtocols())

        serverManager = createInMemoryServerManager(
            testScope = testScope,
            testDispatcherProvider = TestDispatcherProvider(testDispatcher = testDispatcher),
            supportsProtocol = supportsProtocol,
            initialServers = emptyList(),
        )

        val serverManager2 = ServerManager2(
            serverManager = serverManager,
            supportsProtocol = supportsProtocol,
        )

        getDefaultConnectIntent = GetDefaultConnectIntent(
            getIntentAvailability = mockGetIntentAvailability,
            serverManager2 = serverManager2,
        )
    }

    @Test
    fun `GIVEN default connect intent available WHEN getting default connect intent THEN returns default connect intent`() = testScope.runTest {
        val defaultIntentAvailability = ConnectIntentAvailability.NO_SERVERS
        val expectedConnectIntent = ConnectIntent.Default

        vpnUsers.forEach { vpnUser ->
            coEvery {
                mockGetIntentAvailability(
                    connectIntent = ConnectIntent.Default,
                    vpnUser = vpnUser,
                    settingsProtocol = ProtocolSelection.SMART,
                )
            } returns defaultIntentAvailability

            val connectIntent = getDefaultConnectIntent(
                vpnUser = vpnUser,
                protocolSelection = ProtocolSelection.SMART,
            )

            assertEquals(expectedConnectIntent, connectIntent)
        }
    }

    @Test
    fun `GIVEN default connect intent not available AND no gateways available WHEN getting default connect intent THEN returns default connect intent`() = testScope.runTest {
        val defaultIntentAvailability = ConnectIntentAvailability.NO_SERVERS
        val servers = emptyList<Server>()
        serverManager.setServers(
            serverList = servers,
            statusId = "StatusID",
            language = null,
        )
        val expectedConnectIntent = ConnectIntent.Default

        vpnUsers.forEach { vpnUser ->
            coEvery {
                mockGetIntentAvailability(
                    connectIntent = ConnectIntent.Default,
                    vpnUser = vpnUser,
                    settingsProtocol = ProtocolSelection.SMART,
                )
            } returns defaultIntentAvailability

            val connectIntent = getDefaultConnectIntent(
                vpnUser = vpnUser,
                protocolSelection = ProtocolSelection.SMART,
            )

            assertEquals(expectedConnectIntent, connectIntent)
        }
    }

    @Test
    fun `GIVEN default connect intent not available AND gateways available WHEN getting default connect intent THEN returns first available gateway connect intent`() = testScope.runTest {
        val defaultIntentAvailability = ConnectIntentAvailability.NO_SERVERS
        val gatewayName1 = "Test Gateway Name 1"
        val gatewayName2 = "Test Gateway Name 2"
        val servers = listOf(
            createServer(serverId = "Test Server 1", gatewayName = gatewayName1),
            createServer(serverId = "Test Server 2", gatewayName = gatewayName2),
        )
        serverManager.setServers(
            serverList = servers,
            statusId = "StatusID",
            language = null,
        )
        val protocolSelection = ProtocolSelection.SMART
        val expectedConnectIntent = ConnectIntent.Gateway(gatewayName = gatewayName2, serverId = null)

        vpnUsers.forEach { vpnUser ->
            coEvery {
                mockGetIntentAvailability(
                    connectIntent = ConnectIntent.Default,
                    vpnUser = vpnUser,
                    settingsProtocol = ProtocolSelection.SMART,
                )
            } returns defaultIntentAvailability

            coEvery {
                mockGetIntentAvailability(
                    connectIntent = ConnectIntent.Gateway(gatewayName = gatewayName1, serverId = null),
                    vpnUser = vpnUser,
                    settingsProtocol = protocolSelection,
                )
            } returns ConnectIntentAvailability.NO_SERVERS

            coEvery {
                mockGetIntentAvailability(
                    connectIntent = ConnectIntent.Gateway(gatewayName = gatewayName2, serverId = null),
                    vpnUser = vpnUser,
                    settingsProtocol = protocolSelection,
                )
            } returns ConnectIntentAvailability.ONLINE

            val connectIntent = getDefaultConnectIntent(
                vpnUser = vpnUser,
                protocolSelection = ProtocolSelection.SMART,
            )

            assertEquals(expectedConnectIntent, connectIntent)
        }
    }

}
