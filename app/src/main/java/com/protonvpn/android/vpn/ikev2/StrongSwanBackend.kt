/*
 * Copyright (c) 2021 Proton Technologies AG
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
package com.protonvpn.android.vpn.ikev2

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.protonvpn.android.ProtonApplication
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.ConnectionParamsIKEv2
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.ui.home.GetNetZone
import com.protonvpn.android.vpn.CertificateRepository
import com.protonvpn.android.vpn.ErrorType
import com.protonvpn.android.vpn.LocalAgentUnreachableTracker
import com.protonvpn.android.vpn.PrepareResult
import com.protonvpn.android.vpn.RetryInfo
import com.protonvpn.android.vpn.ServerPing
import com.protonvpn.android.vpn.VpnBackend
import com.protonvpn.android.vpn.VpnState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import me.proton.core.network.domain.NetworkManager
import me.proton.core.network.domain.NetworkStatus
import me.proton.core.util.kotlin.DispatcherProvider
import org.strongswan.android.logic.VpnStateService
import java.util.Random
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StrongSwanBackend @Inject constructor(
    val random: Random,
    networkManager: NetworkManager,
    mainScope: CoroutineScope,
    userData: UserData,
    appConfig: AppConfig,
    certificateRepository: CertificateRepository,
    dispatcherProvider: DispatcherProvider,
    serverPing: ServerPing,
    localAgentUnreachableTracker: LocalAgentUnreachableTracker,
    currentUser: CurrentUser,
    getNetZone: GetNetZone,
) : VpnBackend(
    userData,
    appConfig,
    certificateRepository,
    networkManager,
    VpnProtocol.IKEv2,
    mainScope,
    dispatcherProvider,
    serverPing,
    localAgentUnreachableTracker,
    currentUser,
    getNetZone
), VpnStateService.VpnStateListener {

    private var vpnService: VpnStateService? = null
    private val serviceProvider = Channel<VpnStateService>()

    init {
        bindCharonMonitor()
        mainScope.launch {
            networkManager.observe().collect { status ->
                if (status == NetworkStatus.Disconnected && vpnProtocolState != VpnState.Disabled) {
                    vpnProtocolState = VpnState.WaitingForNetwork
                } else {
                    stateChanged()
                }
            }
        }
    }

    private suspend fun getVpnService() = vpnService ?: serviceProvider.receive()

    override suspend fun prepareForConnection(
        profile: Profile,
        server: Server,
        transmissionProtocols: Set<TransmissionProtocol>,
        scan: Boolean,
        numberOfPorts: Int, // unused, IKEv2 uses 2 ports and both need to be functional
        waitForAll: Boolean // as above
    ): List<PrepareResult> {
        val connectingDomain = server.getRandomConnectingDomain()
        val result = listOf(PrepareResult(this, ConnectionParamsIKEv2(profile, server, connectingDomain)))
        return if (!scan)
            result
        else {
            val ports = STRONGSWAN_PORTS
            val availablePorts = scanUdpPorts(connectingDomain, ports, ports.size, true)
            if (availablePorts.toSet() == ports.toSet())
                result
            else
                emptyList()
        }
    }

    override suspend fun connect(connectionParams: ConnectionParams) {
        super.connect(connectionParams)
        getVpnService().connect(null, true)
    }

    override suspend fun closeVpnTunnel(withStateChange: Boolean) {
        vpnService?.disconnect()
        waitForDisconnect()
    }

    override suspend fun reconnect() {
        vpnService?.reconnect()
    }

    override val retryInfo: RetryInfo
        get() = RetryInfo(vpnService!!.retryTimeout, vpnService!!.retryIn)

    private fun bindCharonMonitor() = mainScope.launch {
        val context = ProtonApplication.getAppContext()
        context.bindService(Intent(context, VpnStateService::class.java), object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (binder is VpnStateService.LocalBinder) {
                    vpnService = binder.service.apply {
                        registerListener(this@StrongSwanBackend)
                        mainScope.launch {
                            serviceProvider.send(this@apply)
                        }
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                vpnService = null
            }
        }, Service.BIND_AUTO_CREATE)
    }

    private fun translateState(state: VpnStateService.State, error: VpnStateService.ErrorState): VpnState =
        if (error == VpnStateService.ErrorState.NO_ERROR) when (state) {
            VpnStateService.State.DISABLED -> VpnState.Disabled
            VpnStateService.State.CONNECTING -> VpnState.Connecting
            VpnStateService.State.CONNECTED -> VpnState.Connected
            VpnStateService.State.DISCONNECTING -> VpnState.Disconnecting
        } else {
            VpnState.Error(translateError(error))
        }

    private fun translateError(error: VpnStateService.ErrorState) = when (error) {
        VpnStateService.ErrorState.AUTH_FAILED -> ErrorType.AUTH_FAILED_INTERNAL
        VpnStateService.ErrorState.PEER_AUTH_FAILED -> ErrorType.PEER_AUTH_FAILED
        VpnStateService.ErrorState.LOOKUP_FAILED -> ErrorType.LOOKUP_FAILED_INTERNAL
        VpnStateService.ErrorState.UNREACHABLE -> ErrorType.UNREACHABLE_INTERNAL
        VpnStateService.ErrorState.MULTI_USER_PERMISSION -> ErrorType.MULTI_USER_PERMISSION
        else -> ErrorType.GENERIC_ERROR
    }

    override fun stateChanged() {
        vpnService?.let {
            vpnProtocolState = translateState(it.state, it.errorState)
        }
    }

    companion object {
        private val STRONGSWAN_PORTS = listOf(500, 4500)
    }
}
