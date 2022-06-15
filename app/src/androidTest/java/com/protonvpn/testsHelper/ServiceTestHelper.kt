/*
 * Copyright (c) 2018 Proton Technologies AG
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
package com.protonvpn.testsHelper

import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.test.shared.MockedServers.getProfile
import com.protonvpn.test.shared.MockedServers.serverList
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class ServiceTestHelper {

    private val helper = ServerManagerHelper()
    val stateMonitor = helper.vpnStateMonitor

    val serverManager = helper.serverManager
    val userData = helper.userData
    @JvmField var mockVpnBackend = helper.backend

    @JvmField var connectionManager = helper.vpnConnectionManager

    val isSecureCoreEnabled get() = userData.secureCoreEnabled

    fun addProfile(protocol: VpnProtocol, name: String, serverDomain: String) = runBlocking(Dispatchers.Main){
        var server: Server? = null
        for (s in serverList) {
            if (s.domain == serverDomain) server = s
        }
        checkNotNull(server) { "No mocked server for domain: $serverDomain" }
        val profile = getProfile(protocol, server, name)
        serverManager.addToProfileList(profile)
    }

    fun deleteCreatedProfiles() {
        serverManager.deleteSavedProfiles()
        userData.defaultConnection = null
    }

    fun checkIfConnectedToVPN() = runBlocking(Dispatchers.Main) {
        assertTrue("User was not connected to VPN", stateMonitor.isConnected)
    }

    fun enableSecureCore(state: Boolean) {
        userData.secureCoreEnabled = state
    }

    fun checkIfDisconnectedFromVPN() = runBlocking(Dispatchers.Main) {
        assertFalse("User was not disconnected from VPN", stateMonitor.isConnected)
    }
}
