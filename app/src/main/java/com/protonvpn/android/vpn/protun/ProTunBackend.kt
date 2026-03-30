/*
 * Copyright (c) 2022 Proton AG
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

package com.protonvpn.android.vpn.protun

import android.content.Context
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.usecase.ComputeAllowedIPs
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.redesign.vpn.usecases.SettingsForConnection
import com.protonvpn.android.servers.Server
import com.protonvpn.android.ui.ForegroundActivityTracker
import com.protonvpn.android.ui.home.GetNetZone
import com.protonvpn.android.utils.ifOrNull
import com.protonvpn.android.vpn.CertificateRepository
import com.protonvpn.android.vpn.ErrorType
import com.protonvpn.android.vpn.LocalAgentUnreachableTracker
import com.protonvpn.android.vpn.NetworkCapabilitiesFlow
import com.protonvpn.android.vpn.PrepareResult
import com.protonvpn.android.vpn.VpnBackend
import com.protonvpn.android.vpn.VpnState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.proton.core.network.data.di.SharedOkHttpClient
import me.proton.core.network.domain.NetworkManager
import me.proton.vpn.core.api.ProtonVpnCore
import me.proton.vpn.core.api.VpnDisconnectError
import me.proton.vpn.core.api.VpnWaitReason
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProTunBackend @Inject constructor(
    @ApplicationContext val context: Context,
    networkManager: NetworkManager,
    networkCapabilitiesFlow: NetworkCapabilitiesFlow,
    settingsForConnection: SettingsForConnection,
    certificateRepository: CertificateRepository,
    dispatcherProvider: VpnDispatcherProvider,
    mainScope: CoroutineScope,
    localAgentUnreachableTracker: LocalAgentUnreachableTracker,
    currentUser: CurrentUser,
    getNetZone: GetNetZone,
    foregroundActivityTracker: ForegroundActivityTracker,
    @SharedOkHttpClient okHttp: OkHttpClient,
    private val vpnCore: ProtonVpnCore,
    private val computeAllowedIPs: ComputeAllowedIPs,
    private val preparePeers: PreparePeersForConnectionProTun,
    private val packetCapture: PacketCapture,
) : VpnBackend(
    settingsForConnection, certificateRepository, networkManager, networkCapabilitiesFlow, VpnProtocol.ProTun, mainScope,
    dispatcherProvider, localAgentUnreachableTracker, currentUser, getNetZone, foregroundActivityTracker, okHttp, shouldWaitForTunnelVerified = false
) {
    private var observePcapJob: Job? = null

    init {
        vpnCore.connectionManager.state.onEach { state ->
            ProtonLogger.logCustom(
                LogCategory.PROTOCOL,
                "ProTun state changed: $state"
            )
            vpnProtocolState = when (state) {
                is me.proton.vpn.core.api.VpnConnectionState.Disconnected ->
                    state.error?.let { error ->
                        when (error) {
                            is VpnDisconnectError.ServiceError ->
                                VpnState.Error(ErrorType.GENERIC_ERROR, error.message, isFinal = true)
                            is VpnDisconnectError.TunInterfaceError ->
                                VpnState.Error(ErrorType.GENERIC_ERROR, error.message, isFinal = true)
                            VpnDisconnectError.VpnPermissionMissing ->
                                VpnState.Error(ErrorType.GENERIC_ERROR, "VPN permission missing", isFinal = true)
                            VpnDisconnectError.InteractAcrossUsers ->
                                VpnState.Error(ErrorType.MULTI_USER_PERMISSION, null, isFinal = true)
                        }
                    } ?: VpnState.Disabled
                is me.proton.vpn.core.api.VpnConnectionState.Connecting -> VpnState.Connecting
                is me.proton.vpn.core.api.VpnConnectionState.Connected -> VpnState.Connected
                is me.proton.vpn.core.api.VpnConnectionState.WaitingForAction ->
                    when (state.reason) {
                        VpnWaitReason.WaitingForNetwork -> VpnState.WaitingForNetwork
                    }
                me.proton.vpn.core.api.VpnConnectionState.Loading -> VpnState.Disabled
            }
        }.launchIn(mainScope)
    }

    override suspend fun prepareForConnection(
        connectIntent: AnyConnectIntent,
        server: Server,
        transmissionProtocols: Set<TransmissionProtocol>,
        scan: Boolean,
        numberOfPorts: Int,
        waitForAll: Boolean
    ): List<PrepareResult> {
        val (domain, peers) = preparePeers(server, transmissionProtocols) ?: return emptyList()
        val transmissionType = ifOrNull (transmissionProtocols.size == 1) { transmissionProtocols.first() }
        return listOf(
            PrepareResult(
                this,
                ConnectionParamsProTun(
                    connectIntent,
                    server,
                    domain,
                    peers,
                    transmissionType,
                    settingsForConnection.getFor(connectIntent).ipV6Enabled
                )
            )
        )
    }

    override suspend fun connect(connectionParams: ConnectionParams) {
        super.connect(connectionParams)
        val protunParams = connectionParams as ConnectionParamsProTun
        val settings = settingsForConnection.getFor(protunParams.connectIntent)
        val sessionId = currentUser.sessionId()
        val config = protunParams.getTunnelConfig(
            context,
            settings,
            sessionId,
            certificateRepository,
            computeAllowedIPs,
            packetCapture.activeFileFlow.first()
        )
        vpnCore.connectionManager.connect(config)

        observePcapJob = mainScope.launch {
            packetCapture.activeFileFlow
                .drop(1)
                .collect { pcapFile -> vpnCore.connectionManager.setPacketCaptureEnabled(pcapFile) }
        }
    }

    override suspend fun closeVpnTunnel(withStateChange: Boolean) {
        observePcapJob?.cancel()
        observePcapJob = null

        vpnCore.connectionManager.disconnect()
        if (withStateChange) {
            // Set state to disabled right away to give app some time to close notification
            // as the service might be killed right away on disconnection
            vpnProtocolState = VpnState.Disabled
            delay(10)
        }
    }
}
