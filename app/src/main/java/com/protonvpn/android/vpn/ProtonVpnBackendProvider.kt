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
import com.protonvpn.android.logging.toLog
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import me.proton.core.util.kotlin.mapAsync

private val ALL_TRANSMISSION_PROTOCOLS = TransmissionProtocol.values().toSet()

class ProtonVpnBackendProvider(
    val config: AppConfig,
    val openVpn: VpnBackend,
    val wireGuard: VpnBackend,
    val supportsProtocol: SupportsProtocol,
) : VpnBackendProvider {

    override suspend fun prepareConnection(
        protocol: ProtocolSelection,
        profile: Profile,
        server: Server,
        alwaysScan: Boolean
    ): PrepareResult? {
        ProtonLogger.logCustom(LogCategory.CONN_CONNECT,
            "Preparing connection with protocol: ${protocol.toLog()}")
        val scan = when (protocol.vpn) {
            VpnProtocol.OpenVPN -> alwaysScan
            VpnProtocol.WireGuard -> alwaysScan
            VpnProtocol.Smart -> true
        }
        return when (protocol.vpn) {
            VpnProtocol.OpenVPN ->
                openVpn.prepareForConnection(profile, server, setOf(protocol.transmission!!), scan)
            VpnProtocol.WireGuard ->
                wireGuard.prepareForConnection(profile, server, setOf(protocol.transmission!!), scan)
            VpnProtocol.Smart -> {
                getSmartEnabledBackends(server, null).asFlow().map {
                    val transmissionProtocols =
                        if (it == wireGuard) getSmartWireGuardTransmissionProtocols(null)
                        else ALL_TRANSMISSION_PROTOCOLS
                    it.prepareForConnection(profile, server, transmissionProtocols, scan)
                }.firstOrNull {
                    it.isNotEmpty()
                }
            }
        }?.firstOrNull()
            .also {
                if (scan) logScanResult(it)
            }
    }

    private fun getSmartWireGuardTransmissionProtocols(orgProtocol: ProtocolSelection?) =
        mutableSetOf<TransmissionProtocol>().apply {
            val wireGuardTxxEnabled = config.getFeatureFlags().wireguardTlsEnabled
            with(config.getSmartProtocolConfig()) {
                if (wireguardEnabled) add(TransmissionProtocol.UDP)
                if (wireguardTcpEnabled && wireGuardTxxEnabled) add(TransmissionProtocol.TCP)
                if (wireguardTlsEnabled && wireGuardTxxEnabled) add(TransmissionProtocol.TLS)
                if (orgProtocol?.vpn == VpnProtocol.WireGuard)
                    add(orgProtocol.transmission ?: TransmissionProtocol.UDP)
            }
        }

    override suspend fun pingAll(
        orgProtocol: ProtocolSelection,
        preferenceList: List<PhysicalServer>,
        fullScanServer: PhysicalServer?
    ): VpnBackendProvider.PingResult? {
        val responses = coroutineScope {
            preferenceList.mapAsync { server ->
                val profile = Profile.getTempProfile(server.server)
                val fullScan = server === fullScanServer
                val portsLimit = if (fullScan) Int.MAX_VALUE else PING_ALL_MAX_PORTS
                val responses = getSmartEnabledBackends(server.server, orgProtocol.vpn).mapAsync {
                    val transmissionProtocols =
                        if (it == wireGuard) getSmartWireGuardTransmissionProtocols(orgProtocol)
                        else ALL_TRANSMISSION_PROTOCOLS
                    it.prepareForConnection(
                        profile, server.server, transmissionProtocols, true, portsLimit, waitForAll = fullScan
                    )
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

    private fun getSmartEnabledBackends(server: Server, orgVpnProtocol: VpnProtocol?): List<VpnBackend> = buildList {
        with(config.getSmartProtocolConfig()) {
            val wireGuardTxxEnabled =
                config.getFeatureFlags().wireguardTlsEnabled && (wireguardTcpEnabled || wireguardTlsEnabled)
            val wireGuardEnabled = wireguardEnabled || wireGuardTxxEnabled
            if (wireGuardEnabled && supportsProtocol(server, VpnProtocol.WireGuard))
                add(wireGuard)
            if (openVPNEnabled && supportsProtocol(server, VpnProtocol.OpenVPN))
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
