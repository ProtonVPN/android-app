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
package com.protonvpn.android.models.vpn

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.utils.DebugUtils
import com.protonvpn.android.vpn.CertificateRepository
import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import de.blinkt.openvpn.core.NetworkUtils
import inet.ipaddr.IPAddressString
import inet.ipaddr.ipv4.IPv4Address
import inet.ipaddr.ipv4.IPv4AddressSeqRange
import me.proton.core.network.domain.session.SessionId

class ConnectionParamsWireguard(
    profile: Profile,
    server: Server,
    port: Int,
    connectingDomain: ConnectingDomain,
    entryIp: String?,
    transmission: TransmissionProtocol
) : ConnectionParams(
    profile,
    server,
    connectingDomain,
    VpnProtocol.WireGuard,
    entryIp,
    port,
    transmission,
), java.io.Serializable {

    override val info get() = "${super.info} $transmissionProtocol port: $port"

    @Throws(IllegalStateException::class)
    suspend fun getTunnelConfig(
        context: Context,
        userData: UserData,
        sessionId: SessionId?,
        certificateRepository: CertificateRepository
    ): Config {
        val entryIp = entryIp ?: requireNotNull(connectingDomain?.getEntryIp(protocolSelection))

        // Our modified WireGuard requires server IP excluded as we replaced
        // VpnService.protect with split tunneling to have TCP/TLS socket support.
        val excludedIPs = mutableListOf(entryIp)
        var excludedApps: Set<String> = emptySet()
        if (userData.useSplitTunneling) {
            userData.splitTunnelIpAddresses.takeIf { it.isNotEmpty() }?.let {
                excludedIPs += it
            }
            userData.splitTunnelApps.takeIf { it.isNotEmpty() }?.let {
                excludedApps = it.toSortedSet()
            }
        }
        if (userData.shouldBypassLocalTraffic())
            excludedIPs += NetworkUtils.getLocalNetworks(context, false).toList()

        val allowedIps = calculateAllowedIps(excludedIPs)
        ProtonLogger.logCustom(LogCategory.CONN, "WireGuard port: $port, allowed IPs: $allowedIps")

        val peer = Peer.Builder()
            .parsePublicKey(requireNotNull(connectingDomain?.publicKeyX25519))
            .parseEndpoint("$entryIp:$port")
            .parseAllowedIPs(allowedIps)
            .build()

        val iface = Interface.Builder()
            .parseAddresses("10.2.0.2/32")
            .excludeApplications(excludedApps)
            .parseDnsServers("10.2.0.1")
            .parsePrivateKey(certificateRepository.getX25519Key(sessionId))
            .build()

        return Config.Builder().addPeer(peer).setInterface(iface).build()
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun calculateAllowedIps(excludedIps: List<String>): String {
        val excludedAddrs = excludedIps.asSequence()
            .map { IPAddressString(it).address }
        DebugUtils.debugAssert { excludedAddrs.none { it.isIPv6 } }
        val excludedAddrs4 = excludedAddrs
            .filter { it.isIPv4 }
            .map { it as IPv4Address }

        // Add all IPs
        var ranges = listOf(IPv4AddressSeqRange(IPv4Address(0),
            IPv4Address(byteArrayOf(255.toByte(), 255.toByte(), 255.toByte(), 255.toByte()))))
        // Create IP ranges by removing excluded IPs
        for (ip in excludedAddrs4) {
            val toRemove = IPv4AddressSeqRange(ip, ip)
            ranges = ranges.flatMap { range ->
                if (range.contains(ip))
                    range.subtract(toRemove).toList()
                else
                    listOf(range)
            }
        }
        val allowedIps4 = ranges.flatMap { it.spanWithPrefixBlocks().toList() }
            .joinToString(separator = ", ") { it.toCanonicalString() }

        // Don't leak IPv6 for Wireguard when split tunneling is used
        // Also ::/0 CIDR should not be used for IPv6 as it causes LAN connection issues
        return "$allowedIps4, 2000::/3"
    }
}
