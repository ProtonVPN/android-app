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
package com.protonvpn.android.vpn

import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.logging.ConnConnectScanResult
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.servers.Server
import com.protonvpn.android.utils.AndroidUtils.whenNotNullNorEmpty
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.toLog
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.vpn.protun.ProTunBackend
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import me.proton.core.util.kotlin.mapAsync

class ProtonVpnBackendProvider(
    val config: AppConfig,
    val openVpn: VpnBackend,
    val wireGuard: VpnBackend,
    val proTunBackend: VpnBackend,
    val supportsProtocol: SupportsProtocol,
) : VpnBackendProvider {

    override suspend fun prepareConnection(
        protocol: ProtocolSelection,
        connectIntent: AnyConnectIntent,
        server: Server,
        alwaysScan: Boolean
    ): PrepareResult? {
        ProtonLogger.logCustom(LogCategory.CONN_CONNECT,
            "Preparing connection with protocol: ${protocol.toLog()}")
        val scan = when (protocol.vpn) {
            VpnProtocol.OpenVPN -> alwaysScan
            VpnProtocol.WireGuard -> alwaysScan
            VpnProtocol.ProTun -> false
            VpnProtocol.Smart -> true
        }
        return when (protocol.vpn) {
            VpnProtocol.OpenVPN ->
                openVpn.prepareForConnection(connectIntent, server, setOf(protocol.transmission!!), scan)
            VpnProtocol.WireGuard ->
                wireGuard.prepareForConnection(connectIntent, server, setOf(protocol.transmission!!), scan)
            VpnProtocol.ProTun -> {
                val transmissions = if (protocol.transmission != null)
                    setOf(protocol.transmission)
                else
                    getSmartTransmissionProtocols(VpnProtocol.ProTun, null)
                proTunBackend.prepareForConnection(connectIntent, server, transmissions, scan)
            }
            VpnProtocol.Smart -> {
                getSmartEnabledBackends(server, null).asFlow().map {
                    val transmissionProtocols = getSmartTransmissionProtocols(it.vpnProtocol, null)
                    it.prepareForConnection(connectIntent, server, transmissionProtocols, scan)
                }.firstOrNull {
                    it.isNotEmpty()
                }
            }
        }?.firstOrNull()
            .also {
                if (scan) logScanResult(it)
            }
    }

    private fun getSmartTransmissionProtocols(vpnProtocol: VpnProtocol, orgProtocol: ProtocolSelection?) =
        mutableSetOf<TransmissionProtocol>().apply {
            with(config.getSmartProtocolConfig()) {
                when (vpnProtocol) {
                    VpnProtocol.OpenVPN -> {
                        if (openVPNUdpEnabled) add(TransmissionProtocol.UDP)
                        if (openVPNTcpEnabled) add(TransmissionProtocol.TCP)
                    }
                    VpnProtocol.WireGuard, VpnProtocol.ProTun -> {
                        val wireGuardTxxEnabled = config.getFeatureFlags().wireguardTlsEnabled
                        if (wireguardEnabled) add(TransmissionProtocol.UDP)
                        if (wireguardTcpEnabled && wireGuardTxxEnabled) add(TransmissionProtocol.TCP)
                        if (wireguardTlsEnabled && wireGuardTxxEnabled) add(TransmissionProtocol.TLS)
                    }
                    VpnProtocol.Smart -> {}
                }
                if (orgProtocol?.vpn == vpnProtocol)
                    add(orgProtocol.transmission ?: TransmissionProtocol.UDP)
            }
        }

    override suspend fun pingAll(
        orgIntent: AnyConnectIntent,
        orgProtocol: ProtocolSelection,
        preferenceList: List<PhysicalServer>,
        fullScanServer: PhysicalServer?
    ): VpnBackendProvider.PingResult? {
        val responses = coroutineScope {
            preferenceList.mapAsync { server ->
                val fullScan = server === fullScanServer
                val portsLimit = if (fullScan) Int.MAX_VALUE else PING_ALL_MAX_PORTS
                val responses = getSmartEnabledBackends(server.server, orgProtocol.vpn).mapAsync {
                    val transmissionProtocols = getSmartTransmissionProtocols(it.vpnProtocol, orgProtocol)
                    it.prepareForConnection(
                        orgIntent, server.server, transmissionProtocols, true, portsLimit, waitForAll = fullScan
                    )
                }.flatten()
                server to responses
            }.toMap()
        }

        preferenceList.forEach { server ->
            val serverResponses = responses[server]
            serverResponses.whenNotNullNorEmpty { responses ->
                return VpnBackendProvider.PingResult(server, responses)
            }
        }
        return null
    }

    private fun getSmartEnabledBackends(server: Server, orgVpnProtocol: VpnProtocol?): List<VpnBackend> = buildList {
        with(config.getSmartProtocolConfig()) {
            val wireGuardTxxEnabled =
                config.getFeatureFlags().wireguardTlsEnabled && (wireguardTcpEnabled || wireguardTlsEnabled)
            val wireGuardEnabled = wireguardEnabled || wireGuardTxxEnabled
            if (wireGuardEnabled && supportsProtocol(server, VpnProtocol.WireGuard))
                add(wireGuard)
            val openVpnEnabled = openVPNUdpEnabled || openVPNTcpEnabled
            if (openVpnEnabled && supportsProtocol(server, VpnProtocol.OpenVPN))
                add(openVpn)
            if (orgVpnProtocol != null) {
                getBackendFor(orgVpnProtocol)?.let { orgBackend ->
                    if (!contains(orgBackend))
                        add(orgBackend)
                }
            }
        }
    }

    private fun getBackendFor(vpnProtocol: VpnProtocol) = when(vpnProtocol) {
        VpnProtocol.OpenVPN -> openVpn
        VpnProtocol.WireGuard -> wireGuard
        VpnProtocol.ProTun -> proTunBackend
        VpnProtocol.Smart -> null
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
