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
import android.content.Intent
import androidx.lifecycle.Observer
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class GuestHole(
    private val serverManager: ServerManager,
    private val vpnMonitor: VpnStateMonitor
) {

    suspend fun <T> call(
        context: Context,
        prepareIntentHandler: ((Intent) -> Unit)? = null,
        block: suspend () -> T
    ): T? {
        val servers = serverManager.getRandomServers(GUEST_HOLE_SERVERS_COUNT)
        var result: T? = null
        try {
            servers.any { server ->
                executeConnected(context, server, prepareIntentHandler) {
                    result = block()
                }
            }
        } finally {
            if (!vpnMonitor.isDisabled)
                vpnMonitor.disconnectSync()
        }
        return result
    }

    private suspend fun <T> executeConnected(
        context: Context,
        server: Server,
        prepareIntentHandler: ((Intent) -> Unit)?,
        block: suspend () -> T
    ): Boolean {
        var connected = vpnMonitor.isConnected
        if (!connected) {
            val vpnStatus = vpnMonitor.vpnStatus
            connected = withTimeoutOrNull(GUEST_HOLE_SERVER_TIMEOUT) {
                suspendCancellableCoroutine<Boolean> { continuation ->
                    var observer: Observer<VpnStateMonitor.Status>? = null
                    observer = Observer { newState ->
                        if (newState.state.let { it is VpnState.Connected || it is VpnState.Error }) {
                            vpnStatus.removeObserver(observer!!)
                            continuation.resume(newState.state == VpnState.Connected)
                        }
                    }
                    val profile = Profile.getTempProfile(server, serverManager).apply {
                        setProtocol(VpnProtocol.OpenVPN)
                    }
                    vpnMonitor.connect(context, profile, prepareIntentHandler = prepareIntentHandler)
                    vpnStatus.observeForever(observer)
                    continuation.invokeOnCancellation {
                        vpnStatus.removeObserver(observer)
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
        private const val GUEST_HOLE_SERVER_TIMEOUT = 10_000L
        private const val GUEST_HOLE_SERVERS_COUNT = 5
    }
}
