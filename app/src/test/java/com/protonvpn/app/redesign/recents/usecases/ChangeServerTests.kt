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

package com.protonvpn.app.redesign.recents.usecases

import com.protonvpn.android.appconfig.ChangeServerConfig
import com.protonvpn.android.appconfig.Restrictions
import com.protonvpn.android.appconfig.RestrictionsConfig
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.vpn.ChangeServerManager
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.UnrestrictedChangeServerOnLongConnectEnabled
import com.protonvpn.android.redesign.vpn.ui.ChangeServerViewState
import com.protonvpn.android.redesign.vpn.ui.ChangeServerViewStateFlow
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.ui.home.vpn.ChangeServerPrefs
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.android.vpn.VpnUiDelegate
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createServer
import com.protonvpn.test.shared.runWhileCollecting
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

private const val SMALL_DELAY_S = 30
private const val LARGE_DELAY_S = 120
private const val MAX_ATTEMPTS = 2
private val LONG_CONNECT_TIME = 7.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class ChangeServerTests {

    @RelaxedMockK
    private lateinit var mockVpnConnectionManager: VpnConnectionManager

    @MockK
    private lateinit var mockServerManager: ServerManager2

    @MockK
    private lateinit var mockVpnUiDelegate: VpnUiDelegate

    @MockK
    private lateinit var mockUnrestrictedChangeServerEnabled: UnrestrictedChangeServerOnLongConnectEnabled

    private lateinit var changeServerPrefs: ChangeServerPrefs
    private lateinit var currentUser: CurrentUser
    private lateinit var isFeatureFlagEnabled: MutableStateFlow<Boolean>
    private lateinit var restrictionsConfig: RestrictionsConfig
    private lateinit var testScope: TestScope
    private lateinit var testUserProvider: TestCurrentUserProvider
    private lateinit var vpnStateMonitor: VpnStateMonitor

    private lateinit var changeServerManager: ChangeServerManager
    private lateinit var changeServerViewStateFlow: ChangeServerViewStateFlow

    private val server = createServer("server")
    private val changeServerConfig = ChangeServerConfig(SMALL_DELAY_S, MAX_ATTEMPTS, LARGE_DELAY_S)
    private val connectionParams = ConnectionParams(ConnectIntent.Fastest, server, null, null)
    private val freeUser = TestUser.freeUser.vpnUser
    private val plusUser = TestUser.plusUser.vpnUser

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        testScope = TestScope()
        changeServerPrefs = ChangeServerPrefs(MockSharedPreferencesProvider())
        testUserProvider = TestCurrentUserProvider(vpnUser = freeUser)
        currentUser = CurrentUser(testScope.backgroundScope, testUserProvider)
        vpnStateMonitor = VpnStateMonitor()
        val vpnStatusProviderUI = VpnStatusProviderUI(testScope.backgroundScope, vpnStateMonitor)

        restrictionsConfig =
            RestrictionsConfig(testScope.backgroundScope, flowOf(Restrictions(true, changeServerConfig)))

        coEvery { mockServerManager.getRandomServer(any()) } returns server
        isFeatureFlagEnabled = MutableStateFlow(true)
        every { mockUnrestrictedChangeServerEnabled.isEnabled } returns isFeatureFlagEnabled

        // Advance time away from 0, otherwise the state will be locked because lastChangeTimestamp is 0 on start.
        testScope.advanceTimeBy(60_000)

        changeServerManager = ChangeServerManager(
            testScope.backgroundScope,
            vpnStatusProviderUI,
            restrictionsConfig,
            mockVpnConnectionManager,
            mockServerManager,
            changeServerPrefs,
            currentUser,
            mockUnrestrictedChangeServerEnabled,
            testScope::currentTime,
        )
        changeServerViewStateFlow = ChangeServerViewStateFlow(
            restrictionsConfig,
            changeServerPrefs,
            vpnStatusProviderUI,
            changeServerManager,
            currentUser,
            testScope::currentTime,
        )

        runBlocking {
            // Enable the flow. It is marked as starting lazily to avoid doing unnecessary work for paid users.
            changeServerManager.hasTroubleConnecting.first()
        }
    }

    @Test
    fun `change server shown after first connection`() = testScope.runTest {
        val states = runWhileCollecting(changeServerViewStateFlow) {
            runCurrent()
            vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, connectionParams))
            runCurrent()
        }
        assertEquals(listOf(null, ChangeServerViewState.Unlocked), states)
    }

    @Test
    fun `change server shown while reconnecting due to server change`() = testScope.runTest {
        val states = runWhileCollecting(changeServerViewStateFlow) {
            runCurrent()
            vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, connectionParams))
            runCurrent()
            changeServerManager.changeServer(mockVpnUiDelegate)
            runCurrent()
            vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connecting, connectionParams))
            runCurrent()
        }
        val expected = listOf(
            null,
            ChangeServerViewState.Unlocked,
            ChangeServerViewState.Locked(SMALL_DELAY_S, SMALL_DELAY_S, false),
            ChangeServerViewState.Disabled // while connecting
        )
        assertEquals(expected, states)
    }

    @Test
    fun `change server is locked after first attempt`() = testScope.runTest {
        val states = runWhileCollecting(changeServerViewStateFlow) {
            runCurrent()
            changeServerManager.changeServer(mockVpnUiDelegate)
            vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, connectionParams))
            runCurrent()
        }
        assertEquals(listOf(null, ChangeServerViewState.Locked(SMALL_DELAY_S, SMALL_DELAY_S, false)), states)
    }

    @Test
    fun `several changes lock for longer period of time`() = testScope.runTest {
        val states = runWhileCollecting(changeServerViewStateFlow) {
            repeat(MAX_ATTEMPTS) {
                runCurrent()
                changeServerManager.changeServer(mockVpnUiDelegate)
            }
            runCurrent()
            vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, connectionParams))
            runCurrent()
        }
        assertEquals(listOf(null, ChangeServerViewState.Locked(LARGE_DELAY_S, LARGE_DELAY_S, true)), states)
    }

    @Test
    fun `updating to plus removes change server`() =
        testUpdatingToPlus(VpnState.Connected, ChangeServerViewState.Locked(SMALL_DELAY_S, SMALL_DELAY_S, false))

    @Test
    fun `updating to plus removes change server while connecting`() =
        testUpdatingToPlus(VpnState.Connecting, ChangeServerViewState.Disabled)

    private fun testUpdatingToPlus(
        vpnStateToCheck: VpnState,
        changeServerState: ChangeServerViewState
    ) = testScope.runTest {
        val states = runWhileCollecting(changeServerViewStateFlow) {
            runCurrent()
            changeServerManager.changeServer(mockVpnUiDelegate)
            updateVpnStatus(vpnStateToCheck)
            runCurrent()
            testUserProvider.vpnUser = plusUser
            runCurrent()
        }
        val expected = listOf(
            null,
            changeServerState,
            null
        )
        assertEquals(expected, states)
    }

    @Test
    fun `when connecting takes long then change server appears`() = testScope.runTest {
        val states = runWhileCollecting(changeServerViewStateFlow) {
            updateVpnStatus(VpnState.Connecting, ConnectIntent.Fastest)
            advanceTimeBy(LONG_CONNECT_TIME)
            changeServerManager.changeServer(mockVpnUiDelegate)
            updateVpnStatus(VpnState.Connecting, ConnectIntent.FastestInCountry(CountryId.sweden, emptySet()))
            advanceTimeBy(LONG_CONNECT_TIME)
        }
        val expected = listOf(
            null,
            ChangeServerViewState.Unlocked,
            ChangeServerViewState.Disabled, // after server change
            ChangeServerViewState.Unlocked,
        )
        assertEquals(expected, states)
    }

    @Test
    fun `while locked it's still possible to change server when connecting`() = testScope.runTest {
        repeat(MAX_ATTEMPTS) {
            runCurrent()
            changeServerManager.changeServer(mockVpnUiDelegate)
        }
        val states = runWhileCollecting(changeServerViewStateFlow) {
            updateVpnStatus(VpnState.Connecting, ConnectIntent.Fastest)
            advanceTimeBy(LONG_CONNECT_TIME)
        }
        val expected = listOf(
            ChangeServerViewState.Disabled,
            ChangeServerViewState.Unlocked
        )
        assertEquals(expected, states)
    }

    @Test
    fun `changing server while having trouble connecting doesn't count towards limits`() = testScope.runTest {
        updateVpnStatus(VpnState.Connecting, ConnectIntent.Fastest)
        advanceTimeBy(LONG_CONNECT_TIME)
        changeServerManager.changeServer(mockVpnUiDelegate)
        runCurrent() // changeServer launches a coroutine that updates counters.
        assertEquals(0, changeServerPrefs.changeCounter)
    }

    @Test
    fun `changing server after having trouble connecting counts towards limits`() = testScope.runTest {
        updateVpnStatus(VpnState.Connecting, ConnectIntent.Fastest)
        advanceTimeBy(LONG_CONNECT_TIME)
        updateVpnStatus(VpnState.Connected, ConnectIntent.Fastest)
        runCurrent()
        changeServerManager.changeServer(mockVpnUiDelegate)
        runCurrent() // changeServer launches a coroutine that updates counters.
        assertEquals(1, changeServerPrefs.changeCounter)
    }

    @Test
    fun `waiting for network disables change server button`() = testScope.runTest {
        val state = runWhileCollecting(changeServerViewStateFlow) {
            updateVpnStatus(VpnState.Connecting, ConnectIntent.Fastest)
            advanceTimeBy(LONG_CONNECT_TIME)
            updateVpnStatus(VpnState.WaitingForNetwork, ConnectIntent.Fastest)
            advanceTimeBy(LONG_CONNECT_TIME) // make sure it doesn't get reenabled after timeout.
        }
        val expected = listOf(null, ChangeServerViewState.Unlocked, null)
        assertEquals(expected, state)
    }

    @Test
    fun `when feature flag is disabled don't allow change server on long connect`() = testScope.runTest {
        isFeatureFlagEnabled.value = false
        val states = runWhileCollecting(changeServerViewStateFlow) {
            updateVpnStatus(VpnState.Connecting, ConnectIntent.Fastest)
            advanceTimeBy(LONG_CONNECT_TIME)
        }
        assertEquals(listOf(null), states)
    }

    @Test
    fun `ChangeServerViewState_Locked splits time in minutes and seconds`() {
        val state = ChangeServerViewState.Locked(90, 120, false)
        assertEquals(1, state.remainingTimeMinutes)
        assertEquals(30, state.remainingTimeSeconds)
    }

    private fun updateVpnStatus(vpnState: VpnState, intent: ConnectIntent = ConnectIntent.Fastest) {
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(vpnState, ConnectionParams(intent, server, null, null)))
    }
}
