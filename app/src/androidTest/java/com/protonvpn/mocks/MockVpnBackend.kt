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

import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.vpn.PrepareResult
import com.protonvpn.android.vpn.RetryInfo
import com.protonvpn.android.vpn.VpnBackend
import com.protonvpn.android.vpn.VpnState

class MockVpnBackend(val protocol: VpnProtocol) : VpnBackend("MockVpnBackend") {

    override suspend fun prepareForConnection(profile: Profile, server: Server, scan: Boolean) =
        if (scan && failScanning)
            null
        else PrepareResult(this, object : ConnectionParams(
                profile, server, server.getRandomConnectingDomain(), protocol) {})

    override suspend fun connect() {
        setSelfState(VpnState.Connecting)
        setSelfState(stateOnConnect)
    }

    override suspend fun disconnect() {
        setSelfState(VpnState.Disconnecting)
        setSelfState(VpnState.Disabled)
    }

    override suspend fun reconnect() {
        setSelfState(VpnState.Connecting)
        setSelfState(stateOnConnect)
    }

    override val retryInfo get() = RetryInfo(10, 10)

    var stateOnConnect: VpnState = VpnState.Connected
    var failScanning = false
}
