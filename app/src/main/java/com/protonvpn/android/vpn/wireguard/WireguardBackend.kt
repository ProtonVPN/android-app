package com.protonvpn.android.vpn.wireguard

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

import android.content.Context
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.logging.ConnError
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.ConnectionParamsWireguard
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.wireguard.WireGuardTunnel
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.vpn.CertificateRepository
import com.protonvpn.android.vpn.ErrorType
import com.protonvpn.android.vpn.PrepareResult
import com.protonvpn.android.vpn.RetryInfo
import com.protonvpn.android.vpn.VpnBackend
import com.protonvpn.android.vpn.VpnState
import com.wireguard.android.backend.BackendException
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.core.network.domain.NetworkManager
import me.proton.core.util.kotlin.DispatcherProvider
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.IllegalStateException
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeoutException

class WireguardBackend(
    val context: Context,
    val backend: GoBackend,
    networkManager: NetworkManager,
    userData: UserData,
    appConfig: AppConfig,
    certificateRepository: CertificateRepository,
    dispatcherProvider: DispatcherProvider,
    mainScope: CoroutineScope,
    currentUser: CurrentUser
) : VpnBackend(
    userData, appConfig, certificateRepository, networkManager, VpnProtocol.WireGuard, mainScope,
    dispatcherProvider, currentUser
) {

    private var service: WireguardWrapperService? = null
    private val testTunnel = WireGuardTunnel(
        name = Constants.WIREGUARD_TUNNEL_NAME,
        config = null,
        state = Tunnel.State.DOWN
    )

    init {
        mainScope.launch {
            testTunnel.stateFlow.collect {
                vpnProtocolState = when (it) {
                    Tunnel.State.DOWN -> VpnState.Disabled
                    Tunnel.State.TOGGLE -> VpnState.Connecting
                    Tunnel.State.UP -> VpnState.Connected
                }
            }
        }
    }

    override suspend fun prepareForConnection(
        profile: Profile,
        server: Server,
        scan: Boolean,
        numberOfPorts: Int,
        waitForAll: Boolean
    ): List<PrepareResult> {
        val connectingDomain = server.getRandomConnectingDomain()
        val ports = appConfig.getWireguardPorts().udpPorts
        val selectedPorts = if (scan)
            scanUdpPorts(connectingDomain, ports, numberOfPorts, waitForAll)
        else
            listOfNotNull(ports.first())
        return selectedPorts.map { port ->
            PrepareResult(
                this,
                ConnectionParamsWireguard(
                    profile,
                    server,
                    port,
                    connectingDomain
                )
            )
        }
    }

    override suspend fun connect(connectionParams: ConnectionParams) {
        super.connect(connectionParams)
        val wireguardParams = connectionParams as ConnectionParamsWireguard
        try {
            val config = wireguardParams.getTunnelConfig(
                context, userData, currentUser.sessionId(), certificateRepository
            )
            withContext(Dispatchers.IO) {
                try {
                    backend.setState(testTunnel, Tunnel.State.UP, config)
                } catch (e: BackendException) {
                    if (e.reason == BackendException.Reason.UNABLE_TO_START_VPN && e.cause is TimeoutException) {
                        // GoBackend waits only 2s for the VPN service to start. Sometimes this is not enough, retry.
                        backend.setState(testTunnel, Tunnel.State.UP, config)
                    } else {
                        throw e
                    }
                }
            }
        } catch (e: IllegalStateException) {
            if (e is CancellationException) throw e
            else handleConnectException(e)
        } catch (e: BackendException) {
            handleConnectException(e)
        }
    }

    override suspend fun closeVpnTunnel(withStateChange: Boolean) {
        service?.close()
        if (withStateChange) {
            // Set state to disabled right away to give app some time to close notification
            // as the service might be killed right away on disconnection
            vpnProtocolState = VpnState.Disabled
            delay(10)
        }
        withContext(Dispatchers.IO) { backend.setState(testTunnel, Tunnel.State.DOWN, null) }
    }

    fun serviceCreated(vpnService: WireguardWrapperService) {
        service = vpnService
    }

    fun serviceDestroyed() {
        service = null
    }

    override val retryInfo: RetryInfo? get() = null

    private fun handleConnectException(e: Exception) {
        ProtonLogger.log(
            ConnError,
            "Caught exception while connecting with WireGuard\n" +
                StringWriter().apply { e.printStackTrace(PrintWriter(this)) }.toString()
        )
        // TODO do not use generic error here (depends on other branch)
        selfStateObservable.value = VpnState.Error(ErrorType.GENERIC_ERROR)
    }
}
