/*
 * Copyright (c) 2025 Proton AG
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

import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.appconfig.DefaultPorts
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.servers.Server
import com.protonvpn.android.servers.api.ConnectingDomain
import com.protonvpn.android.vpn.ProtocolSelection
import me.proton.vpn.sdk.api.Peer
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

// Instead of port-pinging with ProTUN we just return all ports and transmission protocols
@Singleton
class PreparePeersForConnectionProTun(
    private val getWireGuardPorts: () -> DefaultPorts,
) {
    @Inject constructor(appConfig: AppConfig) : this(appConfig::getWireguardPorts)

    operator fun invoke(
        server: Server,
        transmissionProtocols: Set<TransmissionProtocol>,
        random: Random = Random.Default,
    ): Pair<ConnectingDomain, List<Peer>>?  {
        // Pick random domain that supports ProTun and at least one of the requested transmission protocols
        val domain = server.connectingDomains.shuffled(random).firstOrNull { domain ->
            domain.publicKeyX25519 != null &&
                transmissionProtocols.any {
                    domain.getEntryIp(ProtocolSelection.Companion(VpnProtocol.ProTun, it)) != null
                }
        }
        if (domain == null) {
            ProtonLogger.logCustom(
                LogCategory.CONN_CONNECT,
                "No compatible domain found for server: ${server.serverName}"
            )
            return null
        }
        val wireguardPorts = getWireGuardPorts()
        val entryIPsToTransmissions = transmissionProtocols.groupBy {
            domain.getEntryIp(ProtocolSelection.Companion(VpnProtocol.ProTun, it))
        }
        val peers = entryIPsToTransmissions.mapNotNull { (entryIp, transmissions) ->
            entryIp?.let {
                Peer(
                    InetAddress.getByName(entryIp),
                    transmissions.associate { transmission ->
                        val protocol = ProtocolSelection(VpnProtocol.ProTun, transmission)
                        val ports = domain.getEntryPorts(protocol)
                            ?: wireguardPorts.forTransmission(transmission)
                        transmission.toSdkProtocol() to ports
                    },
                    requireNotNull(domain.publicKeyX25519),
                    0,
                    server.serverId
                )
            }
        }
        return domain to peers
    }
}

private fun TransmissionProtocol.toSdkProtocol() =
    when (this) {
        TransmissionProtocol.UDP -> me.proton.vpn.sdk.api.VpnProtocol.WireGuardUdp
        TransmissionProtocol.TCP -> me.proton.vpn.sdk.api.VpnProtocol.WireGuardTcp
        TransmissionProtocol.TLS -> me.proton.vpn.sdk.api.VpnProtocol.Stealth
    }

private fun DefaultPorts.forTransmission(transmission: TransmissionProtocol) =
    when (transmission) {
        TransmissionProtocol.UDP -> udpPorts
        TransmissionProtocol.TCP -> tcpPorts
        TransmissionProtocol.TLS -> tlsPorts
    }
