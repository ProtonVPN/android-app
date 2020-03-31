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
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.ConnectionParamsIKEv2
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.vpn.VpnStateMonitor.State
import com.protonvpn.android.vpn.VpnStateMonitor.ErrorType
import com.protonvpn.android.utils.DebugUtils
import com.protonvpn.android.utils.NetUtils
import com.protonvpn.android.utils.implies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.strongswan.android.logic.VpnStateService
import java.io.ByteArrayOutputStream
import java.util.Random

class StrongSwanBackend(
    val random: Random,
    val mainScope: CoroutineScope
) : VpnBackend("StrongSwan"), VpnStateService.VpnStateListener {

    private var vpnService: VpnStateService? = null
    private val serviceProvider = Channel<VpnStateService>()

    init {
        bindCharonMonitor()
    }

    private suspend fun getVpnService() = vpnService ?: serviceProvider.receive()

    override suspend fun prepareForConnection(profile: Profile, server: Server, scan: Boolean): PrepareResult? {
        val connectingDomain = server.getRandomConnectingDomain()
        if (!scan || isServerAvailable(connectingDomain.entryIp))
            return PrepareResult(this, ConnectionParamsIKEv2(profile, server, connectingDomain))
        return null
    }

    private suspend fun isServerAvailable(ip: String) =
        NetUtils.ping(ip, 500, getPingData(), tcp = false, timeout = 5000)

    private fun getPingData() = ByteArrayOutputStream().apply {
        repeat(8) { write(random.nextInt(256)) } // my SPI
        repeat(8) { write(0) } // other SPI
        write(0x21) // Security association
        write(0x20) // Version 2
        write(0x22) // IKE_SA_INIT
        write(0x08) // Initiator, no higher version, request
        repeat(4) { write(0) } // Message id
        repeat(4) { write(0) } // Length = 0
    }.toByteArray()

    override suspend fun connect() {
        getVpnService().connect(null, true)
    }

    override suspend fun disconnect() {
        if (vpnService?.state != VpnStateService.State.DISABLED) {
            selfStateObservable.value = State.Disconnecting
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

    private fun translateState(state: VpnStateService.State, error: VpnStateService.ErrorState): State =
        when (state) {
            VpnStateService.State.DISABLED -> State.Disabled
            VpnStateService.State.CHECKING_AVAILABILITY -> State.CheckingAvailability
            VpnStateService.State.WAITING_FOR_NETWORK -> State.WaitingForNetwork
            VpnStateService.State.CONNECTING -> State.Connecting
            VpnStateService.State.CONNECTED -> State.Connected
            VpnStateService.State.RECONNECTING -> State.Reconnecting
            VpnStateService.State.DISCONNECTING -> State.Disconnecting
            VpnStateService.State.ERROR -> State.Error(when (error) {
                VpnStateService.ErrorState.AUTH_FAILED -> ErrorType.AUTH_FAILED_INTERNAL
                VpnStateService.ErrorState.PEER_AUTH_FAILED -> ErrorType.PEER_AUTH_FAILED
                VpnStateService.ErrorState.LOOKUP_FAILED -> ErrorType.LOOKUP_FAILED
                VpnStateService.ErrorState.UNREACHABLE -> ErrorType.UNREACHABLE
                VpnStateService.ErrorState.SESSION_IN_USE -> ErrorType.SESSION_IN_USE
                VpnStateService.ErrorState.MAX_SESSIONS -> ErrorType.MAX_SESSIONS
                else -> ErrorType.GENERIC_ERROR
            })
        }

    override fun stateChanged() {
        selfStateObservable.postValue(translateState(vpnService!!.state, vpnService!!.errorState))
        DebugUtils.debugAssert {
            (selfState in arrayOf(State.Connecting, State.Connected)).implies(active)
        }
    }
}
