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
package com.protonvpn.mocks

import com.proton.gopenpgp.localAgent.Features
import com.proton.gopenpgp.localAgent.LocalAgent
import com.proton.gopenpgp.localAgent.StatusMessage
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.usecase.GetConnectingDomain
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.ui.ForegroundActivityTracker
import com.protonvpn.android.ui.home.GetNetZone
import com.protonvpn.android.vpn.AgentConnectionInterface
import com.protonvpn.android.vpn.CertificateRepository
import com.protonvpn.android.vpn.LocalAgentUnreachableTracker
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
    certificateRepository: CertificateRepository,
    userSettings: EffectiveCurrentUserSettings,
    appConfig: AppConfig,
    val protocol: VpnProtocol,
    localAgentUnreachableTracker: LocalAgentUnreachableTracker,
    currentUser: CurrentUser,
    getNetZone: GetNetZone,
    foregroundActivityTracker: ForegroundActivityTracker,
    val getConnectingDomain: GetConnectingDomain
) : VpnBackend(
    userSettings = userSettings,
    appConfig = appConfig,
    networkManager = networkManager,
    certificateRepository = certificateRepository,
    vpnProtocol = protocol,
    mainScope = scope,
    dispatcherProvider = dispatcherProvider,
    localAgentUnreachableTracker = localAgentUnreachableTracker,
    currentUser = currentUser,
    getNetZone = getNetZone,
    foregroundActivityTracker = foregroundActivityTracker
) {
    private var agentProvider: MockAgentProvider? = null

    fun setAgentProvider(provider: MockAgentProvider) {
        agentProvider = provider
    }

    override suspend fun prepareForConnection(
        profile: Profile,
        server: Server,
        transmissionProtocols: Set<TransmissionProtocol>,
        scan: Boolean,
        numberOfPorts: Int,
        waitForAll: Boolean
    ): List<PrepareResult> =
        if (scan && failScanning)
            emptyList()
        else listOf(PrepareResult(this, object : ConnectionParams(
            profile, server, getConnectingDomain.random(server, null), protocol, null
        ) {}))

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
        nativeClient: VpnAgentClient
    ) = agentProvider?.invoke(certInfo, hostname, nativeClient) ?: MockAgentConnection(scope, nativeClient)

    var stateOnConnect: VpnState = VpnState.Connected
    var failScanning = false
}

class MockAgentConnection(scope: CoroutineScope, val client: VpnBackend.VpnAgentClient) : AgentConnectionInterface {
    private val constants = LocalAgent.constants()

    init {
        scope.launch {
            yield()
            state = constants.stateConnecting
            state = constants.stateConnected
        }
    }

    override var state: String = constants.stateDisconnected
        set(value) {
            field = value
            client.onState(value)
        }

    override val status: StatusMessage? = null
    override fun setFeatures(features: Features) {}
    override fun sendGetStatus(withStatistics: Boolean) {}
    override fun setConnectivity(connectivity: Boolean) {}
    override fun close() {}
}
