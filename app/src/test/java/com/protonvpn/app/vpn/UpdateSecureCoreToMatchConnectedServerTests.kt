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
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.UpdateSecureCoreToMatchConnectedServer
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.test.shared.MockSharedPreference
import com.protonvpn.test.shared.MockedServers
import com.protonvpn.test.shared.runWhileCollectingLiveData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateSecureCoreToMatchConnectedServerTests {

    @get:Rule
    var rule = InstantTaskExecutorRule()

    private lateinit var scope: TestCoroutineScope
    private lateinit var vpnStateMonitor: VpnStateMonitor
    private lateinit var userData: UserData

    private lateinit var updateSecureCoreToMatchConnectedServer: UpdateSecureCoreToMatchConnectedServer

    @Before
    fun setup() {
        Storage.setPreferences(MockSharedPreference())
        scope = TestCoroutineScope()
        userData = UserData.create()
        vpnStateMonitor = VpnStateMonitor()

        updateSecureCoreToMatchConnectedServer =
            UpdateSecureCoreToMatchConnectedServer(scope, VpnStatusProviderUI(scope, vpnStateMonitor), userData)
    }

    @Test
    fun `when connected to Secure Core server while SC disabled then SC is enabled`() {
        userData.secureCoreEnabled = false
        val secureCoreServer = MockedServers.serverList.first { it.isSecureCoreServer }
        val connectionParams = connectionParamsForServer(secureCoreServer)

        val secureCoreValues = runWhileCollectingLiveData(userData.secureCoreLiveData) {
            vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, connectionParams))
        }

        assertEquals(listOf(false, true), secureCoreValues)
        assertTrue(userData.secureCoreEnabled)
    }

    @Test
    fun `when connected to Secure Core server while SC enabled then nothing is updated`() {
        userData.secureCoreEnabled = true
        val secureCoreServer = MockedServers.serverList.first { it.isSecureCoreServer }
        val connectionParams = connectionParamsForServer(secureCoreServer)

        val secureCoreValues = runWhileCollectingLiveData(userData.secureCoreLiveData) {
            vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, connectionParams))
        }

        assertEquals(listOf(true), secureCoreValues) // Only the initial value is emitted.
        assertTrue(userData.secureCoreEnabled)
    }

    @Test
    fun `when connected to regular server while SC disabled then nothing is updated`() {
        userData.secureCoreEnabled = false
        val regularServer = MockedServers.serverList.first { it.isSecureCoreServer.not() }
        val connectionParams = connectionParamsForServer(regularServer)

        val secureCoreValues = runWhileCollectingLiveData(userData.secureCoreLiveData) {
            vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, connectionParams))
        }

        assertEquals(listOf(false), secureCoreValues) // Only the initial value is emitted.
        assertFalse(userData.secureCoreEnabled)
    }

    @Test
    fun `when connecting to Secure Core server while SC disabled then nothing is updated`() {
        userData.secureCoreEnabled = false
        val secureCoreServer = MockedServers.serverList.first { it.isSecureCoreServer }
        val connectionParams = connectionParamsForServer(secureCoreServer)

        val secureCoreValues = runWhileCollectingLiveData(userData.secureCoreLiveData) {
            vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.ScanningPorts, null))
            vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connecting, connectionParams))
        }

        assertEquals(listOf(false), secureCoreValues)
        assertFalse(userData.secureCoreEnabled)
    }

    private fun connectionParamsForServer(server: Server) = ConnectionParams(
        Profile.getTempProfile(server),
        server,
        server.connectingDomains.first(),
        VpnProtocol.WireGuard
    )
}
