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
import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.logging.ConnError
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.LogLevel
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.ConnectionParamsWireguard
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.wireguard.WireGuardTunnel
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.ui.ForegroundActivityTracker
import com.protonvpn.android.ui.home.GetNetZone
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.DebugUtils
import com.protonvpn.android.vpn.CertificateRepository
import com.protonvpn.android.vpn.ErrorType
import com.protonvpn.android.vpn.LocalAgentUnreachableTracker
import com.protonvpn.android.vpn.NetworkCapabilitiesFlow
import com.protonvpn.android.vpn.PrepareForConnection
import com.protonvpn.android.vpn.PrepareResult
import com.protonvpn.android.vpn.VpnBackend
import com.protonvpn.android.vpn.VpnState
import com.wireguard.android.backend.BackendException
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.core.network.data.di.SharedOkHttpClient
import me.proton.core.network.domain.NetworkManager
import me.proton.core.network.domain.NetworkStatus
import okhttp3.OkHttpClient
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WireguardBackend @Inject constructor(
    @ApplicationContext val context: Context,
    networkManager: NetworkManager,
    networkCapabilitiesFlow: NetworkCapabilitiesFlow,
    effectiveCurrentUserSettings: EffectiveCurrentUserSettings,
    certificateRepository: CertificateRepository,
    dispatcherProvider: VpnDispatcherProvider,
    mainScope: CoroutineScope,
    localAgentUnreachableTracker: LocalAgentUnreachableTracker,
    currentUser: CurrentUser,
    getNetZone: GetNetZone,
    private val prepareForConnection: PrepareForConnection,
    foregroundActivityTracker: ForegroundActivityTracker,
    @SharedOkHttpClient okHttp: OkHttpClient,
) : VpnBackend(
    effectiveCurrentUserSettings, certificateRepository, networkManager, networkCapabilitiesFlow, VpnProtocol.WireGuard, mainScope,
    dispatcherProvider, localAgentUnreachableTracker, currentUser, getNetZone, foregroundActivityTracker, okHttp
) {
    private val wireGuardIo = dispatcherProvider.newSingleThreadDispatcherForInifiniteIo()
    private val backend: GoBackend by lazy { GoBackend(WireguardContextWrapper(context)) }

    private var monitoringJob: Job? = null
    private var service: WireguardWrapperService? = null
    private val testTunnel = WireGuardTunnel(
        name = Constants.WIREGUARD_TUNNEL_NAME,
        config = null,
        state = Tunnel.State.DOWN
    )

    override suspend fun prepareForConnection(
        connectIntent: AnyConnectIntent,
        server: Server,
        transmissionProtocols: Set<TransmissionProtocol>,
        scan: Boolean,
        numberOfPorts: Int,
        waitForAll: Boolean
    ): List<PrepareResult> {
        val protocolInfo = prepareForConnection.prepare(server, vpnProtocol, transmissionProtocols, scan,
            numberOfPorts, waitForAll, PRIMARY_PORT, includeTls = true)
        return protocolInfo.map {
            PrepareResult(
                this,
                ConnectionParamsWireguard(
                    connectIntent,
                    server,
                    it.port,
                    it.connectingDomain,
                    it.entryIp,
                    it.transmissionProtocol)
            )
        }
    }

    override suspend fun connect(connectionParams: ConnectionParams) {
        super.connect(connectionParams)
        vpnProtocolState = VpnState.Connecting
        val wireguardParams = connectionParams as ConnectionParamsWireguard
        try {
            val settings = userSettings.effectiveSettings.first()
            val config = wireguardParams.getTunnelConfig(
                context, settings, currentUser.sessionId(), certificateRepository
            )
            val transmission = wireguardParams.protocolSelection?.transmission ?: TransmissionProtocol.UDP
            val transmissionStr = transmission.toString().lowercase()
            withContext(wireGuardIo) {
                try {
                    backend.setState(testTunnel, Tunnel.State.UP, config, transmissionStr)
                } catch (e: BackendException) {
                    if (e.reason == BackendException.Reason.UNABLE_TO_START_VPN && e.cause is TimeoutException) {
                        // GoBackend waits only 2s for the VPN service to start. Sometimes this is not enough, retry.
                        backend.setState(testTunnel, Tunnel.State.UP, config, transmissionStr)
                    } else {
                        throw e
                    }
                }
                startMonitoringJob()
            }
        } catch (e: SecurityException) {
            if (e.message?.contains("INTERACT_ACROSS_USERS") == true)
                selfStateObservable.value = VpnState.Error(ErrorType.MULTI_USER_PERMISSION, isFinal = true)
            else
                handleConnectException(e)
        } catch (e: IllegalStateException) {
            if (e is CancellationException) throw e
            else handleConnectException(e)
        } catch (e: BackendException) {
            handleConnectException(e)
        }
    }

    private suspend fun startMonitoringJob() {
        monitoringJob = mainScope.launch(dispatcherProvider.infiniteIo) {
            ProtonLogger.logCustom(LogCategory.CONN_WIREGUARD, "start monitoring job")
            val networkJob = launch(dispatcherProvider.Main) {
                networkManager.observe().collect { status ->
                    val isConnected = status != NetworkStatus.Disconnected
                    ProtonLogger.logCustom(LogCategory.CONN_WIREGUARD, "set network available: $isConnected")
                    withContext(wireGuardIo) {
                        backend.setNetworkAvailable(isConnected)
                    }
                }
            }
            try {
                var failCountdown = FAIL_COUNTDOWN_INIT
                var keepRunning = true
                while (keepRunning) {
                    // NOTE: backend.state call is blocking
                    val newState = when (val state = backend.state) {
                        WG_STATE_DISABLED -> {
                            if (vpnProtocolState !is VpnState.Error)
                                VpnState.Disabled else null
                        }
                        WG_STATE_CONNECTING ->
                            VpnState.Connecting
                        WG_STATE_CONNECTED ->
                            VpnState.Connected
                        WG_STATE_ERROR -> {
                            failCountdown--
                            if (failCountdown <= 0) {
                                failCountdown = FAIL_COUNTDOWN_INIT
                                VpnState.Error(ErrorType.UNREACHABLE_INTERNAL, isFinal = false)
                            } else {
                                VpnState.Connecting
                            }
                        }
                        WG_STATE_WAITING_FOR_NETWORK -> {
                            VpnState.WaitingForNetwork
                        }
                        WG_STATE_CLOSED -> {
                            keepRunning = false
                            VpnState.Disabled
                        }
                        else -> {
                            DebugUtils.fail("unexpected WireGuard state $state")
                            ProtonLogger.logCustom(
                                LogLevel.ERROR, LogCategory.CONN_WIREGUARD, "unexpected WireGuard state $state"
                            )
                            VpnState.Error(ErrorType.GENERIC_ERROR, isFinal = true)
                        }
                    }
                    mainScope.launch {
                        newState?.let { state ->
                            if (state != vpnProtocolState ||
                                    (state as? VpnState.Error)?.type == ErrorType.UNREACHABLE_INTERNAL)
                                vpnProtocolState = state
                        }
                    }
                }
            } finally {
                networkJob.cancel()
                ProtonLogger.logCustom(LogCategory.CONN_WIREGUARD, "stop monitoring job")
            }
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
        withContext(wireGuardIo) {
            backend.setState(testTunnel, Tunnel.State.DOWN, null)
        }
    }

    fun serviceCreated(vpnService: WireguardWrapperService) {
        service = vpnService
    }

    fun serviceDestroyed() {
        service = null
    }

    private fun handleConnectException(e: Exception) {
        ProtonLogger.log(
            ConnError,
            "Caught exception while connecting with WireGuard\n" +
                StringWriter().apply { e.printStackTrace(PrintWriter(this)) }.toString()
        )
        // TODO do not use generic error here (depends on other branch)
        selfStateObservable.value = VpnState.Error(ErrorType.GENERIC_ERROR, isFinal = true)
    }

    companion object {
        const val WG_STATE_CLOSED = -1
        const val WG_STATE_DISABLED = 0
        const val WG_STATE_CONNECTING = 1
        const val WG_STATE_CONNECTED = 2
        const val WG_STATE_ERROR = 3
        const val WG_STATE_WAITING_FOR_NETWORK = 4

        const val FAIL_COUNTDOWN_INIT = 5

        private const val PRIMARY_PORT = 443
    }
}
