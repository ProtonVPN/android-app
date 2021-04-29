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
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.wireguard.WireGuardTunnel
import com.protonvpn.android.models.vpn.wireguard.ConfigProxy
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

class WireguardBackend(
    val backend: GoBackend,
    val userData: UserData,
    val appConfig: AppConfig,
    mainScope: CoroutineScope
) : VpnBackend("WireGuard") {

    private val testTunnel = WireGuardTunnel(
        name = "test",
        config = null,
        state = Tunnel.State.TOGGLE
    )

    init {
        mainScope.launch {
            testTunnel.stateFlow.collect {
                when (it) {
                    Tunnel.State.DOWN -> selfStateObservable.value = VpnState.Disabled
                    Tunnel.State.TOGGLE -> selfStateObservable.value = VpnState.Connecting
                    Tunnel.State.UP -> selfStateObservable.value = VpnState.Connected
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
                ConnectionParams(
                    profile,
                    server,
                    server.getRandomConnectingDomain(),
                    VpnProtocol.WireGuard
                )
            )
        )
    }

    override suspend fun connect() {
        val config = ConfigProxy()
        /*
        To not leak test env information with git history, please change these locally for now
        config.interfaceProxy.addresses = ""
        config.interfaceProxy.dnsServers = ""
        config.interfaceProxy.privateKey = ""
        */

        val peerProxy = config.addPeer()
        /*
        peerProxy.publicKey = ""
        peerProxy.endpoint = ""
        peerProxy.allowedIps = "0.0.0.0/0"
        */
        config.resolve()
        withContext(Dispatchers.IO) {
            backend.setState(testTunnel, Tunnel.State.UP, config.resolve())
        }
    }

    override suspend fun disconnect() {
        if (selfState != VpnState.Disabled) {
            selfStateObservable.value = VpnState.Disconnecting
        }
        withContext(Dispatchers.IO) { backend.setState(testTunnel, Tunnel.State.DOWN, null) }
    }

    override suspend fun reconnect() {
        disconnect()
    }

    override val retryInfo: RetryInfo? get() = null
}
