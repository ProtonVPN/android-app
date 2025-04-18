/*
 * Copyright (c) 2019 Proton AG
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
package com.protonvpn.mocks

import com.proton.gopenpgp.localAgent.Features
import com.proton.gopenpgp.localAgent.LocalAgent
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.usecase.GetConnectingDomain
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.redesign.vpn.usecases.SettingsForConnection
import com.protonvpn.android.ui.ForegroundActivityTracker
import com.protonvpn.android.ui.home.GetNetZone
import com.protonvpn.android.vpn.AgentConnectionInterface
import com.protonvpn.android.vpn.CertificateRepository
import com.protonvpn.android.vpn.LocalAgentUnreachableTracker
import com.protonvpn.android.vpn.NetworkCapabilitiesFlow
import com.protonvpn.android.vpn.PrepareResult
import com.protonvpn.android.vpn.VpnBackend
import com.protonvpn.android.vpn.VpnState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import me.proton.core.network.domain.NetworkManager

typealias MockAgentProvider = (
    certInfo: CertificateRepository.CertificateResult.Success,
    hostname: String?,
    nativeClient: VpnBackend.VpnAgentClient
) -> AgentConnectionInterface

class MockVpnBackend(
    val scope: CoroutineScope,
    dispatcherProvider: VpnDispatcherProvider,
    networkManager: NetworkManager,
    networkCapabilitiesFlow: NetworkCapabilitiesFlow,
    certificateRepository: CertificateRepository,
    settingsForConnection: SettingsForConnection,
    val protocol: VpnProtocol,
    localAgentUnreachableTracker: LocalAgentUnreachableTracker,
    currentUser: CurrentUser,
    getNetZone: GetNetZone,
    foregroundActivityTracker: ForegroundActivityTracker,
    val getConnectingDomain: GetConnectingDomain,
) : VpnBackend(
    settingsForConnection = settingsForConnection,
    networkManager = networkManager,
    networkCapabilitiesFlow = networkCapabilitiesFlow,
    certificateRepository = certificateRepository,
    vpnProtocol = protocol,
    mainScope = scope,
    dispatcherProvider = dispatcherProvider,
    localAgentUnreachableTracker = localAgentUnreachableTracker,
    currentUser = currentUser,
    getNetZone = getNetZone,
    foregroundActivityTracker = foregroundActivityTracker,
    shouldWaitForTunnelVerified = false,
) {
    private var agentProvider: MockAgentProvider? = null

    fun setAgentProvider(provider: MockAgentProvider) {
        agentProvider = provider
    }

    override suspend fun prepareForConnection(
        connectIntent: AnyConnectIntent,
        server: Server,
        transmissionProtocols: Set<TransmissionProtocol>,
        scan: Boolean,
        numberOfPorts: Int,
        waitForAll: Boolean
    ): List<PrepareResult> =
        if (scan && failScanning) {
            emptyList()
        } else {
            val connectionParams = object :
                ConnectionParams(connectIntent, server, getConnectingDomain.random(server, null), protocol, null) {}
            listOf(PrepareResult(this, connectionParams))
        }

    override suspend fun connect(connectionParams: ConnectionParams) {
        super.connect(connectionParams)
        yield() // Simulate a real suspending function, giving it a chance to be cancelled.
        vpnProtocolState = VpnState.Connecting
        vpnProtocolState = stateOnConnect
    }

    override suspend fun closeVpnTunnel(withStateChange: Boolean) {
        yield() // Simulate a real suspending function, giving it a chance to be cancelled.
        vpnProtocolState = VpnState.Disconnecting
        vpnProtocolState = VpnState.Disabled
    }

    override fun createAgentConnection(
        certInfo: CertificateRepository.CertificateResult.Success,
        hostname: String?,
        nativeClient: VpnAgentClient,
        features: Features,
    ) = agentProvider?.invoke(certInfo, hostname, nativeClient) ?: MockAgentConnection(scope, nativeClient, certInfo)

    var stateOnConnect: VpnState = VpnState.Connected
    var failScanning = false
}

class MockAgentConnection(
    scope: CoroutineScope,
    private val client: VpnBackend.VpnAgentClient,
    override val certInfo: CertificateRepository.CertificateResult.Success
) : AgentConnectionInterface {
    private val constants = LocalAgent.constants()

    init {
        scope.launch {
            yield()
            client.onState(constants.stateConnecting)
            client.onState(constants.stateConnected)
        }
    }

    override val lastState: String? get() = client.lastState
    override fun setFeatures(features: Features) {}
    override fun sendGetStatus(withStatistics: Boolean) {}
    override fun setConnectivity(connectivity: Boolean) {}
    override fun close() {}
}
