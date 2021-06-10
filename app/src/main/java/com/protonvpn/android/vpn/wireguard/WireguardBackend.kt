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

import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.ConnectionParamsWireguard
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.wireguard.WireGuardTunnel
import com.protonvpn.android.vpn.CertificateRepository
import com.protonvpn.android.vpn.ErrorType
import com.protonvpn.android.vpn.PrepareResult
import com.protonvpn.android.vpn.RetryInfo
import com.protonvpn.android.vpn.VpnBackend
import com.protonvpn.android.vpn.VpnState
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.core.network.domain.NetworkManager

class WireguardBackend(
    val backend: GoBackend,
    networkManager: NetworkManager,
    userData: UserData,
    appConfig: AppConfig,
    certificateRepository: CertificateRepository,
    mainScope: CoroutineScope
) : VpnBackend(userData, appConfig, certificateRepository, networkManager, VpnProtocol.WireGuard, mainScope) {

    private val testTunnel = WireGuardTunnel(
        name = "test",
        config = null,
        state = Tunnel.State.TOGGLE
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
        numberOfPorts: Int
    ): List<PrepareResult> {
        return listOf(
            PrepareResult(
                this,
                ConnectionParamsWireguard(
                    profile,
                    server,
                    server.getRandomConnectingDomain()
                )
            )
        )
    }

    override suspend fun connect(connectionParams: ConnectionParams) {
        super.connect(connectionParams)
        val wireguardParams = connectionParams as ConnectionParamsWireguard
        try {
            val config = wireguardParams.getTunnelConfig(userData, certificateRepository)
            withContext(Dispatchers.IO) {
                backend.setState(testTunnel, Tunnel.State.UP, config)
            }
        } catch (e: IllegalStateException) {
            // TODO do not use generic error here (depends on other branch)
            selfStateObservable.value = VpnState.Error(ErrorType.GENERIC_ERROR)
        }
    }

    override suspend fun disconnect() {
        if (selfState != VpnState.Disabled) {
            selfStateObservable.value = VpnState.Disconnecting
        }
        withContext(Dispatchers.IO) { backend.setState(testTunnel, Tunnel.State.DOWN, null) }
    }

    override suspend fun reconnect() {
        lastConnectionParams?.let {
            disconnect()
            connect(it)
        }
    }

    override val retryInfo: RetryInfo? get() = null
}
