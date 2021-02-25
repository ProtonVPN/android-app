/*
 * Copyright (c) 2020 Proton Technologies AG
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
package com.protonvpn.android.api

import android.content.Context
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.utils.FileUtils
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.ListSerializer
import kotlin.coroutines.resume

class GuestHole(
    private val scope: CoroutineScope,
    private val serverManager: ServerManager,
    private val vpnMonitor: VpnStateMonitor,
    private val vpnConnectionManager: VpnConnectionManager,
) {

    suspend fun <T> call(
        context: Context,
        block: suspend () -> T
    ): T? {
        var result: T? = null
        try {
            getGuestHoleServers().any { server ->
                executeConnected(context, server) {
                    result = block()
                }
            }
        } finally {
            if (!vpnMonitor.isDisabled)
                vpnConnectionManager.disconnectSync()
        }
        return result
    }

    private fun getGuestHoleServers(): List<Server> {
        val servers = FileUtils.getObjectFromAssets(ListSerializer(Server.serializer()), GUEST_HOLE_SERVERS_ASSET)
        val shuffledServers = servers.shuffled().take(GUEST_HOLE_SERVER_COUNT)
        serverManager.setGuestHoleServers(shuffledServers)
        return shuffledServers
    }

    private suspend fun <T> executeConnected(
        context: Context,
        server: Server,
        block: suspend () -> T
    ): Boolean {
        var connected = vpnMonitor.isConnected
        if (!connected) {
            val vpnStatus = vpnMonitor.status
            connected = withTimeoutOrNull(GUEST_HOLE_SERVER_TIMEOUT) {
                suspendCancellableCoroutine<Boolean> { continuation ->
                    val profile = Profile.getTempProfile(server, serverManager).apply {
                        // Using OpenVPN instead of Smart due to memory corruption bug on native level
                        // with gosrp and Strongswan
                        setProtocol(VpnProtocol.OpenVPN)
                    }
                    vpnConnectionManager.connect(context, profile)
                    val observerJob = scope.launch {
                        vpnStatus.collect { newState ->
                            if (newState.state.let { it is VpnState.Connected || it is VpnState.Error }) {
                                coroutineContext.cancel()
                                continuation.resume(newState.state == VpnState.Connected)
                            }
                        }
                    }
                    continuation.invokeOnCancellation {
                        observerJob.cancel()
                    }
                }
            } == true
        }
        if (connected) {
            block()
        }
        return connected
    }

    companion object {
        private const val GUEST_HOLE_SERVER_COUNT = 5
        private const val GUEST_HOLE_SERVER_TIMEOUT = 10_000L
        private const val GUEST_HOLE_SERVERS_ASSET = "GuestHoleServers.json"
    }
}
