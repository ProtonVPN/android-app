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
package com.protonvpn.android.vpn

import androidx.lifecycle.asLiveData
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.vpn.VpnState.Connected
import com.protonvpn.android.vpn.VpnState.Disabled
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class VpnStateMonitor {

    val status = MutableStateFlow(Status(Disabled, null))
    val onDisconnectedByUser = MutableSharedFlow<Unit>()
    val fallbackConnectionFlow = MutableSharedFlow<SwitchServerReason>()

    // Temporary for poor java classes
    val statusLiveData = status.asLiveData()

    val state get() = status.value.state
    val connectionParams get() = status.value.connectionParams

    val isConnected get() = state == Connected && connectionParams != null
    val isEstablishingConnection get() = state.isEstablishingConnection
    val isEstablishingOrConnected get() = isConnected || state.isEstablishingConnection
    val isDisabled get() = state == Disabled

    val connectingToServer
        get() = connectionParams?.server?.takeIf {
            state == Connected || state.isEstablishingConnection
        }

    val connectionProfile
        get() = connectionParams?.profile

    val isConnectingToSecureCore
        get() = connectingToServer?.isSecureCoreServer == true

    val connectionProtocol
        get() = connectionParams?.protocol

    val exitIP
        get() = connectionParams?.exitIpAddress

    fun isConnectedTo(server: Server?) =
        isConnected && connectionParams?.server?.serverId == server?.serverId

    fun isConnectingToCountry(country: String) =
        connectingToServer?.exitCountry == country

    fun isConnectedToAny(servers: List<Server>) =
        isConnected && connectionParams?.server?.domain?.let { connectingToDomain ->
            connectingToDomain in servers.asSequence().map { it.domain }
        } == true

    data class Status(
        val state: VpnState,
        val connectionParams: ConnectionParams?
    ) {
        val profile get() = connectionParams?.profile
        val server get() = connectionParams?.server
    }
}
