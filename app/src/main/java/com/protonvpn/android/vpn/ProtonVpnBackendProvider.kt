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

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import com.protonvpn.android.ProtonApplication
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.utils.DebugUtils
import com.protonvpn.android.utils.Log
import com.protonvpn.android.utils.implies
import de.blinkt.openpvpn.core.ConnectionStatus
import de.blinkt.openpvpn.core.VpnStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.strongswan.android.logic.VpnStateService

class ProtonVpnBackendProvider : VpnBackendProvider {

    private val openVpn = OpenVpnBackend()
    private val strongSwan = StrongSwanBackend()

    override fun getFor(userData: UserData, profile: Profile?) =
            if (profile?.isOpenVPNSelected(userData) ?: userData.isOpenVPNSelected)
                openVpn else strongSwan
}

class StrongSwanBackend : VpnBackend("StrongSwan"), VpnStateService.VpnStateListener {

    private val mainScope = CoroutineScope(GlobalScope.coroutineContext + Dispatchers.Main)
    private var vpnService: VpnStateService? = null
    private val serviceProvider = Channel<VpnStateService>()

    init {
        bindCharonMonitor()
    }

    private suspend fun getVpnService() = (vpnService ?: serviceProvider.receive())

    override suspend fun connect() {
        getVpnService().connect(null, true)
    }

    override suspend fun disconnect() {
        if (vpnService?.state != VpnStateService.State.DISABLED) {
            stateObservable.value = VpnStateMonitor.State.DISCONNECTING
        }
        vpnService?.disconnect()
        while (state != VpnStateMonitor.State.DISABLED) {
            delay(200)
        }
    }

    override suspend fun reconnect() {
        vpnService?.reconnect()
    }

    override val retryInfo: RetryInfo?
        get() = RetryInfo(vpnService!!.retryTimeout, vpnService!!.retryIn)

    private fun bindCharonMonitor() = mainScope.launch {
        val context = ProtonApplication.getAppContext()
        context.bindService(Intent(context, VpnStateService::class.java), object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                vpnService = (service as VpnStateService.LocalBinder).service.apply {
                    registerListener(this@StrongSwanBackend)
                    mainScope.launch {
                        serviceProvider.send(this@apply)
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                vpnService = null
            }
        }, Service.BIND_AUTO_CREATE)
    }

    private fun translateError(error: VpnStateService.ErrorState): VpnStateMonitor.ErrorState {
        return when (error) {
            VpnStateService.ErrorState.NO_ERROR -> VpnStateMonitor.ErrorState.NO_ERROR
            VpnStateService.ErrorState.AUTH_FAILED -> VpnStateMonitor.ErrorState.AUTH_FAILED_INTERNAL
            VpnStateService.ErrorState.PEER_AUTH_FAILED -> VpnStateMonitor.ErrorState.PEER_AUTH_FAILED
            VpnStateService.ErrorState.LOOKUP_FAILED -> VpnStateMonitor.ErrorState.LOOKUP_FAILED
            VpnStateService.ErrorState.UNREACHABLE -> VpnStateMonitor.ErrorState.UNREACHABLE
            VpnStateService.ErrorState.SESSION_IN_USE -> VpnStateMonitor.ErrorState.SESSION_IN_USE
            VpnStateService.ErrorState.MAX_SESSIONS -> VpnStateMonitor.ErrorState.MAX_SESSIONS
            VpnStateService.ErrorState.GENERIC_ERROR -> VpnStateMonitor.ErrorState.GENERIC_ERROR
        }
    }

    private fun translateState(state: VpnStateService.State): VpnStateMonitor.State {
        return when (state) {
            VpnStateService.State.DISABLED -> VpnStateMonitor.State.DISABLED
            VpnStateService.State.CHECKING_AVAILABILITY -> VpnStateMonitor.State.CHECKING_AVAILABILITY
            VpnStateService.State.WAITING_FOR_NETWORK -> VpnStateMonitor.State.WAITING_FOR_NETWORK
            VpnStateService.State.CONNECTING -> VpnStateMonitor.State.CONNECTING
            VpnStateService.State.CONNECTED -> VpnStateMonitor.State.CONNECTED
            VpnStateService.State.RECONNECTING -> VpnStateMonitor.State.RECONNECTING
            VpnStateService.State.DISCONNECTING -> VpnStateMonitor.State.DISCONNECTING
            VpnStateService.State.ERROR -> VpnStateMonitor.State.ERROR
        }
    }

    override fun stateChanged() {
        error.errorState = translateError(vpnService!!.errorState)
        stateObservable.value = if (error.errorState == VpnStateMonitor.ErrorState.NO_ERROR)
            translateState(vpnService!!.state) else VpnStateMonitor.State.ERROR
        DebugUtils.debugAssert {
            (stateObservable.value in arrayOf(VpnStateMonitor.State.CONNECTING, VpnStateMonitor.State.CONNECTED)).implies(active)
        }
    }
}

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
        do {
            delay(200)
        } while (state != VpnStateMonitor.State.DISABLED)
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

        if (level == ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET && error.errorState == VpnStateMonitor.ErrorState.PEER_AUTH_FAILED) {
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
            (translatedState in arrayOf(VpnStateMonitor.State.CONNECTING, VpnStateMonitor.State.CONNECTED)).implies(active)
        }
        stateObservable.postValue(translatedState)
    }

    override fun setConnectedVPN(uuid: String) {
        Log.e("set connected vpn: $uuid")
    }
}
