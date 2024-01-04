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
import com.protonvpn.android.redesign.vpn.ChangeServerManager
import com.protonvpn.android.redesign.vpn.ConnectIntent
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
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private const val SMALL_DELAY_S = 30
private const val LARGE_DELAY_S = 120
private const val MAX_ATTEMPTS = 2

@OptIn(ExperimentalCoroutinesApi::class)
class ChangeServerTests {

    @RelaxedMockK
    private lateinit var mockVpnConnectionManager: VpnConnectionManager

    @MockK
    private lateinit var mockServerManager: ServerManager2

    @MockK
    private lateinit var mockVpnUiDelegate: VpnUiDelegate

    private lateinit var changeServerPrefs: ChangeServerPrefs
    private lateinit var currentUser: CurrentUser
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

        testScope = TestScope(UnconfinedTestDispatcher())
        changeServerPrefs = ChangeServerPrefs(MockSharedPreferencesProvider())
        testUserProvider = TestCurrentUserProvider(vpnUser = freeUser)
        currentUser = CurrentUser(testScope.backgroundScope, testUserProvider)
        vpnStateMonitor = VpnStateMonitor()
        val vpnStatusProviderUI = VpnStatusProviderUI(testScope.backgroundScope, vpnStateMonitor)

        restrictionsConfig =
            RestrictionsConfig(testScope.backgroundScope, flowOf(Restrictions(true, changeServerConfig)))

        coEvery { mockServerManager.getRandomServer(any()) } returns server

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
    }

    @Test
    fun `change server shown after first connection`() = testScope.runTest {
        val states = runWhileCollecting(changeServerViewStateFlow) {
            vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, connectionParams))
        }
        assertEquals(listOf(null, ChangeServerViewState.Unlocked), states)
    }

    @Test
    fun `change server shown while reconnecting due to server change`() = testScope.runTest {
        val states = runWhileCollecting(changeServerViewStateFlow) {
            vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, connectionParams))
            changeServerManager.changeServer(mockVpnUiDelegate)
            vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connecting, connectionParams))
        }
        val expected = listOf(
            null,
            ChangeServerViewState.Unlocked,
            ChangeServerViewState.Locked(SMALL_DELAY_S, SMALL_DELAY_S, false)
        )
        assertEquals(expected, states)
    }

    @Test
    fun `change server is locked after first attempt`() = testScope.runTest {
        val states = runWhileCollecting(changeServerViewStateFlow) {
            changeServerManager.changeServer(mockVpnUiDelegate)
            vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connecting, connectionParams))
        }
        assertEquals(listOf(null, ChangeServerViewState.Locked(SMALL_DELAY_S, SMALL_DELAY_S, false)), states)
    }

    @Test
    fun `several changes lock for longer period of time`() = testScope.runTest {
        val states = runWhileCollecting(changeServerViewStateFlow) {
            repeat(MAX_ATTEMPTS) {
                changeServerManager.changeServer(mockVpnUiDelegate)
            }
            vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connecting, connectionParams))
        }
        assertEquals(listOf(null, ChangeServerViewState.Locked(LARGE_DELAY_S, LARGE_DELAY_S, true)), states)
    }

    @Test
    fun `updating to plus removes change server`() = testScope.runTest {
        val states = runWhileCollecting(changeServerViewStateFlow) {
            changeServerManager.changeServer(mockVpnUiDelegate)
            vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connecting, connectionParams))
            testUserProvider.vpnUser = plusUser
        }
        val expected = listOf(
            null,
            ChangeServerViewState.Locked(SMALL_DELAY_S, SMALL_DELAY_S, false),
            null
        )
        assertEquals(expected, states)
    }

    @Test
    fun `ChangeServerViewState_Locked splits time in minutes and seconds`() {
        val state = ChangeServerViewState.Locked(90, 120, false)
        assertEquals(1, state.remainingTimeMinutes)
        assertEquals(30, state.remainingTimeSeconds)
    }
}
