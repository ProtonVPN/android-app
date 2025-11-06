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
import com.protonvpn.android.vpn.CertificateRepository
import com.protonvpn.android.vpn.ErrorType
import com.protonvpn.android.vpn.LocalAgentUnreachableTracker
import com.protonvpn.android.vpn.NetworkCapabilitiesFlow
import com.protonvpn.android.vpn.PrepareResult
import com.protonvpn.android.vpn.VpnBackend
import com.protonvpn.android.vpn.VpnState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import me.proton.core.network.data.di.SharedOkHttpClient
import me.proton.core.network.domain.NetworkManager
import me.proton.vpn.sdk.api.ProtonVpnSdk
import me.proton.vpn.sdk.api.VpnWaitReason
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
    private val sdk: ProtonVpnSdk,
    private val computeAllowedIPs: ComputeAllowedIPs,
    private val preparePeers: PreparePeersForConnectionProTun
) : VpnBackend(
    settingsForConnection, certificateRepository, networkManager, networkCapabilitiesFlow, VpnProtocol.ProTun, mainScope,
    dispatcherProvider, localAgentUnreachableTracker, currentUser, getNetZone, foregroundActivityTracker, okHttp, shouldWaitForTunnelVerified = false
) {
    init {
        sdk.connectionManager.state.onEach { state ->
            ProtonLogger.logCustom(
                LogCategory.PROTOCOL,
                "ProTun state changed: $state"
            )
            vpnProtocolState = when (state) {
                is me.proton.vpn.sdk.api.VpnConnectionState.Disconnected ->
                    state.error?.let {
                        VpnState.Error(ErrorType.GENERIC_ERROR, "", isFinal = true)
                    } ?: VpnState.Disabled
                is me.proton.vpn.sdk.api.VpnConnectionState.Connecting -> VpnState.Connecting
                is me.proton.vpn.sdk.api.VpnConnectionState.Connected -> VpnState.Connected
                is me.proton.vpn.sdk.api.VpnConnectionState.WaitingForAction ->
                    when (state.reason) {
                        VpnWaitReason.WaitingForNetwork -> VpnState.WaitingForNetwork
                    }
                me.proton.vpn.sdk.api.VpnConnectionState.Loading -> VpnState.Disabled
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
        return listOf(
            PrepareResult(
                this,
                ConnectionParamsProTun(
                    connectIntent,
                    server,
                    domain,
                    peers,
                    settingsForConnection.getFor(connectIntent).ipV6Enabled
                )
            )
        )
    }

    override suspend fun connect(connectionParams: ConnectionParams) {
        super.connect(connectionParams)
        val wireGuardParams = connectionParams as ConnectionParamsProTun
        val settings = settingsForConnection.getFor(wireGuardParams.connectIntent)
        val sessionId = currentUser.sessionId()
        val config = wireGuardParams.getTunnelConfig(
            context, settings, sessionId, certificateRepository, computeAllowedIPs)
        sdk.connectionManager.connect(config)
    }

    override suspend fun closeVpnTunnel(withStateChange: Boolean) {
        sdk.connectionManager.disconnect()
        if (withStateChange) {
            // Set state to disabled right away to give app some time to close notification
            // as the service might be killed right away on disconnection
            vpnProtocolState = VpnState.Disabled
            delay(10)
        }
    }
}
