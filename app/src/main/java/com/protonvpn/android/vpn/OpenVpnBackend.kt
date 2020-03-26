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

import android.content.Intent
import android.os.Build
import com.protonvpn.android.ProtonApplication
import com.protonvpn.android.utils.DebugUtils
import com.protonvpn.android.utils.Log
import com.protonvpn.android.utils.implies
import de.blinkt.openpvpn.core.ConnectionStatus
import de.blinkt.openpvpn.core.VpnStatus

class OpenVpnBackend : VpnBackend("OpenVpn"), VpnStatus.StateListener {

    init {
        VpnStatus.addStateListener(this)
    }

    override suspend fun connect() {
        startOpenVPN(null)
    }

    override suspend fun disconnect() {
        if (state != VpnStateMonitor.State.DISABLED) {
            stateObservable.value = VpnStateMonitor.State.DISCONNECTING
        }
        // In some scenarios OpenVPN might start a connection in a moment even if it's in the
        // disconnected state - request pause regardless of the state
        startOpenVPN(OpenVPNWrapperService.PAUSE_VPN)
        waitForDisconnect()
    }

    override suspend fun reconnect() {
        disconnect()
        startOpenVPN(null)
    }

    // No retry info available for open vpn
    override val retryInfo: RetryInfo? get() = null

    private fun startOpenVPN(action: String?) {
        val ovpnService =
                Intent(ProtonApplication.getAppContext(), OpenVPNWrapperService::class.java)
        if (action != null)
            ovpnService.action = action
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ProtonApplication.getAppContext().startForegroundService(ovpnService)
        } else {
            ProtonApplication.getAppContext().startService(ovpnService)
        }
    }

    override fun updateState(state: String, logmessage: String, localizedResId: Int, level: ConnectionStatus) {
        var errorState = VpnStateMonitor.ErrorState.NO_ERROR

        if (level == ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET &&
                error.errorState == VpnStateMonitor.ErrorState.PEER_AUTH_FAILED) {
            // On tls-error OpenVPN will send a single RECONNECTING state update with tls-error in
            // logmessage followed by LEVEL_CONNECTING_NO_SERVER_REPLY_YET updates without info
            // about tls-error. Let's stay in PEER_AUTH_FAILED for the rest of this connection
            // attempt.
            return
        }

        val translatedState = if (state == "RECONNECTING" && logmessage.startsWith("tls-error")) {
            errorState = VpnStateMonitor.ErrorState.PEER_AUTH_FAILED
            VpnStateMonitor.State.ERROR
        } else if (state == "RECONNECTING") {
            VpnStateMonitor.State.RECONNECTING
        } else when (level) {
            ConnectionStatus.LEVEL_CONNECTED ->
                VpnStateMonitor.State.CONNECTED
            ConnectionStatus.LEVEL_CONNECTING_SERVER_REPLIED, ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET,
            ConnectionStatus.LEVEL_START, ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT ->
                VpnStateMonitor.State.CONNECTING
            ConnectionStatus.LEVEL_NONETWORK ->
                VpnStateMonitor.State.WAITING_FOR_NETWORK
            ConnectionStatus.LEVEL_NOTCONNECTED, ConnectionStatus.LEVEL_VPNPAUSED ->
                VpnStateMonitor.State.DISABLED
            ConnectionStatus.LEVEL_AUTH_FAILED -> {
                errorState = VpnStateMonitor.ErrorState.AUTH_FAILED_INTERNAL
                VpnStateMonitor.State.ERROR
            }
            ConnectionStatus.UNKNOWN_LEVEL -> {
                errorState = VpnStateMonitor.ErrorState.GENERIC_ERROR
                VpnStateMonitor.State.ERROR
            }
        }
        error.errorState = errorState
        DebugUtils.debugAssert {
            (translatedState in arrayOf(
                    VpnStateMonitor.State.CONNECTING, VpnStateMonitor.State.CONNECTED)).implies(active)
        }
        stateObservable.postValue(translatedState)
    }

    override fun setConnectedVPN(uuid: String) {
        Log.e("set connected vpn: $uuid")
    }
}
