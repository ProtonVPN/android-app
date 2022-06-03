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
package com.protonvpn.android.vpn

import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.logging.ConnConnectScanResult
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.utils.AndroidUtils.whenNotNullNorEmpty
import com.protonvpn.android.logging.ProtonLogger
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import me.proton.core.util.kotlin.mapAsync

class ProtonVpnBackendProvider(
    val config: AppConfig,
    val strongSwan: VpnBackend,
    val openVpn: VpnBackend,
    val wireGuard: VpnBackend,
) : VpnBackendProvider {

    override suspend fun prepareConnection(
        protocol: VpnProtocol,
        profile: Profile,
        server: Server,
        alwaysScan: Boolean
    ): PrepareResult? {
        ProtonLogger.logCustom(LogCategory.CONN_CONNECT, "Preparing connection with protocol: " + protocol.name)
        val scan = when (protocol) {
            VpnProtocol.IKEv2 -> false
            VpnProtocol.OpenVPN -> alwaysScan
            VpnProtocol.WireGuard -> alwaysScan
            VpnProtocol.Smart -> true
        }
        return when (protocol) {
            VpnProtocol.IKEv2 -> strongSwan.prepareForConnection(profile, server, scan)
            VpnProtocol.OpenVPN -> openVpn.prepareForConnection(profile, server, scan)
            VpnProtocol.WireGuard -> wireGuard.prepareForConnection(profile, server, scan)
            VpnProtocol.Smart -> {
                getEnabledBackends(server).asFlow().map {
                    it.prepareForConnection(profile, server, scan)
                }.firstOrNull {
                    it.isNotEmpty()
                }
            }
        }?.firstOrNull()
            .also {
                if (scan) logScanResult(it)
            }
    }

    override suspend fun pingAll(
        preferenceList: List<PhysicalServer>,
        fullScanServer: PhysicalServer?
    ): VpnBackendProvider.PingResult? {
        val responses = coroutineScope {
            preferenceList.mapAsync { server ->
                val profile = Profile.getTempProfile(server.server)
                val fullScan = server === fullScanServer
                val portsLimit = if (fullScan) Int.MAX_VALUE else PING_ALL_MAX_PORTS

                val responses = listOf(wireGuard, strongSwan, openVpn).mapAsync {
                    it.prepareForConnection(profile, server.server, true, portsLimit, waitForAll = fullScan)
                }.flatten()
                server to responses
            }.toMap()
        }

        preferenceList.forEach { server ->
            val serverResponses = responses[server]
            serverResponses.whenNotNullNorEmpty { responses ->
                return VpnBackendProvider.PingResult(responses.first().connectionParams.profile, server, responses)
            }
        }
        return null
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun getEnabledBackends(server: Server) = buildList {
        with(config.getSmartProtocolConfig()) {
            if (wireguardEnabled && server.supportsProtocol(VpnProtocol.WireGuard))
                add(wireGuard)
            if (ikeV2Enabled)
                add(strongSwan)
            if (openVPNEnabled)
                add(openVpn)
        }
    }

    private fun logScanResult(result: PrepareResult?) {
        if (result == null) {
            ProtonLogger.log(ConnConnectScanResult, "no result")
        } else {
            ProtonLogger.log(ConnConnectScanResult, "Connect to: ${result.connectionParams.info}")
        }
    }

    companion object {
        private const val PING_ALL_MAX_PORTS = 3
    }
}
