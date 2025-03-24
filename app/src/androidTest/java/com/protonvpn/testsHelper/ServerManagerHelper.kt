/*
 * Copyright (c) 2019 Proton AG
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

import com.protonvpn.android.ProtonApplication
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.userstorage.ProfileManager
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.ProtonVpnBackendProvider
import com.protonvpn.android.vpn.VpnBackendProvider
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.mocks.MockVpnBackend
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class ServerManagerHelper {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ServerManagerHelperEntryPoint {
        fun vpnStateMonitor(): VpnStateMonitor
        fun vpnConnectionManager(): VpnConnectionManager
        fun serverManager(): ServerManager
        fun profileManager(): ProfileManager
        fun userSettingsManager(): CurrentUserLocalSettingsManager
        fun mockVpnBackendProvider(): VpnBackendProvider
    }

    @JvmField var vpnStateMonitor: VpnStateMonitor
    @JvmField var vpnConnectionManager: VpnConnectionManager
    @JvmField var serverManager: ServerManager
    @JvmField var profileManager: ProfileManager
    @JvmField var userSettingsManager: CurrentUserLocalSettingsManager
    @JvmField var mockVpnBackendProvider: VpnBackendProvider

    val backend: MockVpnBackend
        get() = (mockVpnBackendProvider as ProtonVpnBackendProvider).wireGuard as MockVpnBackend

    init {
        runBlocking(Dispatchers.Main) {
            val hiltEntry = EntryPoints.get(
                ProtonApplication.getAppContext(), ServerManagerHelperEntryPoint::class.java)
            vpnStateMonitor = hiltEntry.vpnStateMonitor()
            vpnConnectionManager = hiltEntry.vpnConnectionManager()
            serverManager = hiltEntry.serverManager()
            profileManager = hiltEntry.profileManager()
            userSettingsManager = hiltEntry.userSettingsManager()
            mockVpnBackendProvider = hiltEntry.mockVpnBackendProvider()
        }
    }
}
