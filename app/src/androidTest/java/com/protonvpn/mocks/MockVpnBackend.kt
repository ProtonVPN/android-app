/*
 * Copyright (c) 2019 Proton Technologies AG
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
package com.protonvpn.mocks

import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.vpn.PrepareResult
import com.protonvpn.android.vpn.RetryInfo
import com.protonvpn.android.vpn.VpnBackend
import com.protonvpn.android.vpn.VpnBackendProvider
import com.protonvpn.android.vpn.VpnStateMonitor

class MockVpnBackendProvider : VpnBackendProvider {
    val backend = MockVpnBackend()

    override suspend fun prepareConnection(profile: Profile, server: Server, userData: UserData): PrepareResult? =
        backend.prepareForConnection(profile, server, false)
}

class MockVpnBackend : VpnBackend("MockVpnBackend") {

    override suspend fun prepareForConnection(profile: Profile, server: Server, scan: Boolean) =
        PrepareResult(this, object : ConnectionParams(
                profile, server, server.getRandomConnectingDomain(), VpnProtocol.IKEv2) {})

    override suspend fun connect() {
        error.errorState = errorOnConnect
        stateObservable.value = VpnStateMonitor.State.CONNECTING
        stateObservable.value = stateOnConnect
    }

    override suspend fun disconnect() {
        stateObservable.value = VpnStateMonitor.State.DISCONNECTING
        error.errorState = VpnStateMonitor.ErrorState.NO_ERROR
        stateObservable.value = VpnStateMonitor.State.DISABLED
    }

    override suspend fun reconnect() {
        error.errorState = errorOnConnect
        stateObservable.value = VpnStateMonitor.State.CONNECTING
        stateObservable.value = stateOnConnect
    }

    override val retryInfo get() = RetryInfo(10, 10)

    var errorOnConnect = VpnStateMonitor.ErrorState.NO_ERROR
    var stateOnConnect = VpnStateMonitor.State.CONNECTED
}
