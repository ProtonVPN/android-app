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

import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.concurrency.DefaultDispatcherProvider
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.vpn.AgentConnectionInterface
import com.protonvpn.android.vpn.CertificateRepository
import com.protonvpn.android.vpn.PrepareResult
import com.protonvpn.android.vpn.RetryInfo
import com.protonvpn.android.vpn.VpnBackend
import com.protonvpn.android.vpn.VpnState
import kotlinx.coroutines.CoroutineScope
import me.proton.core.network.domain.NetworkManager
import com.proton.gopenpgp.localAgent.NativeClient
import com.protonvpn.android.auth.usecase.CurrentUser
import kotlinx.coroutines.yield

typealias MockAgentProvider = (
    certInfo: CertificateRepository.CertificateResult.Success,
    hostname: String?,
    nativeClient: NativeClient
) -> AgentConnectionInterface

class MockVpnBackend(
    scope: CoroutineScope,
    networkManager: NetworkManager,
    certificateRepository: CertificateRepository,
    userData: UserData,
    appConfig: AppConfig,
    val protocol: VpnProtocol,
    currentUser: CurrentUser
) : VpnBackend(
    userData = userData,
    appConfig = appConfig,
    networkManager = networkManager,
    certificateRepository = certificateRepository,
    vpnProtocol = protocol,
    mainScope = scope,
    dispatcherProvider = DefaultDispatcherProvider(),
    currentUser = currentUser
) {
    private var agentProvider: MockAgentProvider? = null

    fun setAgentProvider(provider: MockAgentProvider) {
        agentProvider = provider
    }

    override suspend fun prepareForConnection(
        profile: Profile,
        server: Server,
        scan: Boolean,
        numberOfPorts: Int,
        waitForAll: Boolean
    ): List<PrepareResult> =
        if (scan && failScanning)
            emptyList()
        else listOf(PrepareResult(this, object : ConnectionParams(
                profile, server, server.getRandomConnectingDomain(), protocol) {}))

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
    ) = agentProvider?.invoke(certInfo, hostname, nativeClient)
            ?: super.createAgentConnection(certInfo, hostname, nativeClient)

    override val retryInfo get() = RetryInfo(10, 10)

    var stateOnConnect: VpnState = VpnState.Connected
    var failScanning = false
}
