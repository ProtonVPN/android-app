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

package com.protonvpn.app.vpn

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.settings.data.LocalUserSettingsStoreProvider
import com.protonvpn.android.vpn.UpdateSecureCoreToMatchConnectedServer
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.test.shared.InMemoryDataStoreFactory
import com.protonvpn.test.shared.MockedServers
import com.protonvpn.test.shared.runWhileCollecting
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateSecureCoreToMatchConnectedServerTests {

    @get:Rule
    var rule = InstantTaskExecutorRule()

    private lateinit var testScope: TestScope
    private lateinit var vpnStateMonitor: VpnStateMonitor
    private lateinit var userSettingsManager: CurrentUserLocalSettingsManager

    private lateinit var updateSecureCoreToMatchConnectedServer: UpdateSecureCoreToMatchConnectedServer

    @Before
    fun setup() {
        testScope = TestScope(UnconfinedTestDispatcher())
        vpnStateMonitor = VpnStateMonitor()
        val vpnStatusProviderUI = VpnStatusProviderUI(testScope.backgroundScope, vpnStateMonitor)
        userSettingsManager = CurrentUserLocalSettingsManager(
            LocalUserSettingsStoreProvider(InMemoryDataStoreFactory())
        )

        updateSecureCoreToMatchConnectedServer =
            UpdateSecureCoreToMatchConnectedServer(testScope.backgroundScope, vpnStatusProviderUI, userSettingsManager)
    }

    @Test
    fun `when connected to Secure Core server while SC disabled then SC is enabled`() = testScope.runTest {
        userSettingsManager.updateSecureCore(false)
        val secureCoreServer = MockedServers.serverList.first { it.isSecureCoreServer }
        val connectionParams = connectionParamsForServer(secureCoreServer)

        val secureCoreValues = runWhileCollecting(userSettingsManager.rawCurrentUserSettingsFlow) {
            vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, connectionParams))
        }.map { it.secureCore }

        assertEquals(listOf(false, true), secureCoreValues)
    }

    @Test
    fun `when connected to Secure Core server while SC enabled then nothing is updated`() = testScope.runTest {
        userSettingsManager.updateSecureCore(true)
        val secureCoreServer = MockedServers.serverList.first { it.isSecureCoreServer }
        val connectionParams = connectionParamsForServer(secureCoreServer)

        val secureCoreValues = runWhileCollecting(userSettingsManager.rawCurrentUserSettingsFlow) {
            vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, connectionParams))
        }.map { it.secureCore }

        assertEquals(listOf(true), secureCoreValues) // Only the initial value is emitted.
    }

    @Test
    fun `when connected to regular server while SC disabled then nothing is updated`() = testScope.runTest {
        userSettingsManager.updateSecureCore(false)
        val regularServer = MockedServers.serverList.first { it.isSecureCoreServer.not() }
        val connectionParams = connectionParamsForServer(regularServer)

        val secureCoreValues = runWhileCollecting(userSettingsManager.rawCurrentUserSettingsFlow) {
            vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, connectionParams))
        }.map { it.secureCore }

        assertEquals(listOf(false), secureCoreValues) // Only the initial value is emitted.
    }

    @Test
    fun `when connecting to Secure Core server while SC disabled then nothing is updated`() = testScope.runTest {
        userSettingsManager.updateSecureCore(false)
        val secureCoreServer = MockedServers.serverList.first { it.isSecureCoreServer }
        val connectionParams = connectionParamsForServer(secureCoreServer)

        val secureCoreValues = runWhileCollecting(userSettingsManager.rawCurrentUserSettingsFlow) {
            vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.ScanningPorts, null))
            vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connecting, connectionParams))
        }.map { it.secureCore }

        assertEquals(listOf(false), secureCoreValues)
    }

    private fun connectionParamsForServer(server: Server) = ConnectionParams(
        ConnectIntent.Server(server.serverId, emptySet()),
        server,
        server.connectingDomains.first(),
        VpnProtocol.WireGuard
    )
}
