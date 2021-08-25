/*
 * Copyright (c) 2021. Proton Technologies AG
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

package com.protonvpn.android.ui.home.vpn

import androidx.lifecycle.ViewModel
import com.protonvpn.android.R
import com.protonvpn.android.models.profiles.ProfileColor.Companion.random
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.TrafficMonitor
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class VpnStateConnectedViewModel @Inject constructor(
    private val stateMonitor: VpnStateMonitor,
    private val serverManager: ServerManager
) : ViewModel() {

    data class ConnectionState(
        val serverName: String,
        val serverLoad: Int,
        val serverLoadState: Server.LoadState,
        val exitIp: String,
        val protocol: String
    )

    val eventNotification = MutableSharedFlow<Int>(extraBufferCapacity = 1)

    val connectionState = stateMonitor.status.map { toConnectionState(it) }

    fun saveToProfile() {
        stateMonitor.connectionProfile?.server?.let { currentServer ->
            for (profile in serverManager.getSavedProfiles()) {
                if (profile.server?.domain == currentServer.domain) {
                    eventNotification.tryEmit(R.string.saveProfileAlreadySaved)
                    return
                }
            }
            serverManager.addToProfileList(currentServer.serverName, random(), currentServer)
            eventNotification.tryEmit(R.string.toastProfileSaved)
        }
    }

    private fun toConnectionState(vpnStatus: VpnStateMonitor.Status): ConnectionState =
        if (vpnStatus.state is VpnState.Connected) {
            with(requireNotNull(vpnStatus.connectionParams)) {
                ConnectionState(
                    server.serverName,
                    server.load.toInt(),
                    server.loadState,
                    exitIpAddress ?: "-",
                    requireNotNull(protocol).displayName()
                )
            }
        } else {
            ConnectionState("-", 0, Server.LoadState.LOW_LOAD, "-", "-")
        }
}
