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

package com.protonvpn.android.vpn

import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.logging.ConnConnectScan
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.vpn.ConnectingDomain
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.usecase.GetConnectingDomain
import com.protonvpn.android.utils.DebugUtils
import com.protonvpn.android.utils.takeRandomStable
import me.proton.core.util.kotlin.filterNullValues
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrepareForConnection @Inject constructor(
    private val appConfig: AppConfig,
    private val serverAvailabilityCheck: ServerAvailabilityCheck,
    private val getConnectingDomain: GetConnectingDomain
) {
    data class ProtocolInfo(
        val connectingDomain: ConnectingDomain,
        val transmissionProtocol: TransmissionProtocol,
        val entryIp: String,
        val port: Int
    )

    suspend fun prepare(
        server: Server,
        vpnProtocol: VpnProtocol,
        transmissionProtocols: Set<TransmissionProtocol>,
        scan: Boolean,
        numberOfPorts: Int,
        waitForAll: Boolean,
        primaryTcpPort: Int,
        includeTls: Boolean,
    ): List<ProtocolInfo> {
        return if (!scan) {
            DebugUtils.debugAssert { transmissionProtocols.size == 1 }
            val transmission = transmissionProtocols.first()
            val protocol = ProtocolSelection(vpnProtocol, transmission)
            val connectingDomain = getConnectingDomain.random(server, protocol) ?: run {
                ProtonLogger.log(ConnConnectScan, "${protocol.apiName} not supported on ${server.displayName}")
                return emptyList()
            }
            val ports = connectingDomain.getEntryPorts(protocol) ?: getFallbackPorts(protocol, appConfig)
            val entryIp = connectingDomain.getEntryIp(protocol)
            if (entryIp == null || ports.isEmpty())
                return emptyList()
            listOf(ProtocolInfo(connectingDomain, transmission, entryIp, ports.random()))
        } else {
            scanPorts(server, numberOfPorts, vpnProtocol, transmissionProtocols, waitForAll,
                primaryTcpPort, includeTls = includeTls)
        }
    }

    private fun getFallbackPorts(
        protocol: ProtocolSelection,
        appConfig: AppConfig
    ) = when (protocol.vpn) {
        VpnProtocol.OpenVPN -> appConfig.getOpenVPNPorts()
        VpnProtocol.WireGuard -> appConfig.getWireguardPorts()
        VpnProtocol.Smart -> error("Real protocol expected")
    }.let {
        if (protocol.transmission == TransmissionProtocol.UDP)
            it.udpPorts
        else
            it.tcpPorts
    }

    private suspend fun scanPorts(
        server: Server,
        numberOfPorts: Int,
        vpnProtocol: VpnProtocol,
        transmissionProtocols: Set<TransmissionProtocol>,
        waitForAll: Boolean,
        primaryTcpPort: Int,
        includeTls: Boolean = false // true for protocols that support TLS transport
    ): List<ProtocolInfo> {
        val result = mutableListOf<ProtocolInfo>()
        val transmissions =
            if (includeTls) transmissionProtocols
            else transmissionProtocols.minus(TransmissionProtocol.TLS)
        val transmissionsToConnectingDomains = transmissions.associateWith { transmission ->
            getConnectingDomain.random(server, ProtocolSelection(vpnProtocol, transmission))
        }.filterNullValues()
        val destinations = transmissionsToConnectingDomains.mapValues { (transmission, connectingDomain) ->
            val protocol = ProtocolSelection(vpnProtocol, transmission)
            val ip = requireNotNull(connectingDomain.getEntryIp(protocol))
            val allPorts = connectingDomain.getEntryPorts(protocol) ?: getFallbackPorts(protocol, appConfig)
            val ports = samplePorts(
                allPorts, numberOfPorts, if (transmission == TransmissionProtocol.UDP) null else primaryTcpPort)
            ProtonLogger.log(
                ConnConnectScan,
                "${connectingDomain.entryDomain}/$ip/$vpnProtocol, ${transmission.name} ports: $ports"
            )
            ServerAvailabilityCheck.Destination(ip, ports, connectingDomain.publicKeyX25519)
        }
        serverAvailabilityCheck
            .pingInParallel(destinations, waitForAll)
            .forEach { (transmission, destination) ->
                destination.ports.forEach {
                    result += ProtocolInfo(
                        requireNotNull(transmissionsToConnectingDomains[transmission]),
                        transmission,
                        destination.ip,
                        it)
                }
            }
        return result
    }

    private fun samplePorts(list: List<Int>, count: Int, primaryPort: Int? = null) =
        if (primaryPort != null && list.contains(primaryPort))
            list.filter { it != primaryPort }.takeRandomStable(count - 1) + primaryPort
        else
            list.takeRandomStable(count)
}
