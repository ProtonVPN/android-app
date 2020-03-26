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
package com.protonvpn.android.vpn

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.protonvpn.android.ProtonApplication
import com.protonvpn.android.utils.DebugUtils
import com.protonvpn.android.utils.implies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.strongswan.android.logic.VpnStateService

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
        waitForDisconnect()
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
            (stateObservable.value in arrayOf(
                    VpnStateMonitor.State.CONNECTING, VpnStateMonitor.State.CONNECTED)).implies(active)
        }
    }
}
