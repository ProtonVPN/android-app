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
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.settings.data.SplitTunnelingSettings
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.DebugUtils
import com.protonvpn.android.vpn.CertificateRepository
import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import de.blinkt.openvpn.core.NetworkUtils
import de.blinkt.openvpn.core.removeIpFromRanges
import inet.ipaddr.IPAddress
import inet.ipaddr.IPAddressString
import inet.ipaddr.ipv4.IPv4Address
import inet.ipaddr.ipv4.IPv4AddressSeqRange
import me.proton.core.network.domain.session.SessionId

private const val WG_CLIENT_IP = "10.2.0.2"
private const val WG_SERVER_IP = "10.2.0.1"

private typealias LocalNetworksProvider = (ipv6: Boolean) -> List<String>

class ConnectionParamsWireguard(
    connectIntent: AnyConnectIntent,
    server: Server,
    port: Int,
    connectingDomain: ConnectingDomain,
    entryIp: String?,
    transmission: TransmissionProtocol
) : ConnectionParams(
    connectIntent,
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
        userSettings: LocalUserSettings,
        sessionId: SessionId?,
        certificateRepository: CertificateRepository
    ): Config =
        getTunnelConfig(
            context.packageName,
            { ipv6: Boolean -> NetworkUtils.getLocalNetworks(context, ipv6).toList() },
            userSettings,
            sessionId,
            certificateRepository
        )

    @Throws(IllegalStateException::class)
    @VisibleForTesting
    suspend fun getTunnelConfig(
        myPackageName: String,
        localNetworksProvider: LocalNetworksProvider,
        userSettings: LocalUserSettings,
        sessionId: SessionId?,
        certificateRepository: CertificateRepository
    ): Config {
        val entryIp = entryIp ?: requireNotNull(connectingDomain?.getEntryIp(protocolSelection))

        val allowedIps4String = if (connectIntent is AnyConnectIntent.GuestHole) {
            "0.0.0.0/0"
        } else {
            val allowedIps = allowedIps(localNetworksProvider, userSettings.splitTunneling, userSettings.lanConnections)
            allowedIps.joinToString(separator = ", ") { it.toCanonicalString() }
        }
        // Don't leak IPv6 for Wireguard when split tunneling is used
        // Also ::/0 CIDR should not be used for IPv6 as it causes LAN connection issues
        val allowedIpsString = "$allowedIps4String, 2000::/3"

        ProtonLogger.logCustom(LogCategory.CONN, "WireGuard port: $port, allowed IPs: $allowedIpsString")

        val peer = Peer.Builder()
            .parsePublicKey(requireNotNull(connectingDomain?.publicKeyX25519))
            .parseEndpoint("$entryIp:$port")
            .parseAllowedIPs(allowedIpsString)
            // Having persistent keepalive ensures that WG handshake will be triggered even if no data goes through
            // the tunnel - which fixes the issue on some devices where handshake is not triggered and we're stuck in
            // connecting state.
            .setPersistentKeepalive(60)
            .build()

        val splitTunneling = userSettings.splitTunneling
        val iface = Interface.Builder()
            .parseAddresses("${WG_CLIENT_IP}/32")
            .parseDnsServers(WG_SERVER_IP)
            .parsePrivateKey(certificateRepository.getX25519Key(sessionId))
            .splitTunnelingApps(connectIntent, myPackageName, splitTunneling)
            .build()

        return Config.Builder().addPeer(peer).setInterface(iface).build()
    }

    private fun allowedIps(
        localNetworksProvider: LocalNetworksProvider,
        splitTunneling: SplitTunnelingSettings,
        allowLan: Boolean
    ): List<IPAddress> {
        val includeOnly = with (splitTunneling) {
            isEnabled && mode == SplitTunnelingMode.INCLUDE_ONLY && includedIps.isNotEmpty()
        }
        return if (includeOnly) {
            val alwaysIncluded = setOf(Constants.LOCAL_AGENT_IP, WG_SERVER_IP)
            val includeIps = splitTunneling.includedIps
                .map { IPAddressString(it).address }
                .filter { it.isIPv4 && !it.toCanonicalString().startsWith("127.") }
                .flatMap { it.spanWithPrefixBlocks().toList() }
                .toMutableList()

            // Note: allow LAN is ignored, mostly for consistency with OpenVPN where this behavior is implemented in
            // OpenVPNService. LAN is accessible in INCLUDE_ONLY mode anyway unless the user explicitly configures
            // LAN IPs to go via VPN - there should be no reason to do this.

            includeIps + alwaysIncluded.map { IPAddressString(it).address }
        } else {
            val excludedIps = mutableListOf<String>()
            if (splitTunneling.isEnabled && splitTunneling.mode == SplitTunnelingMode.EXCLUDE_ONLY)
                excludedIps += splitTunneling.excludedIps

            if (allowLan)
                excludedIps += localNetworksProvider(false)

            excludedIpsToAllowedIps(excludedIps)
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun excludedIpsToAllowedIps(excludedIps: List<String>): List<IPAddress> {
        val excludedAddrs = excludedIps.asSequence()
            .map { IPAddressString(it).address }
        DebugUtils.debugAssert { excludedAddrs.none { it.isIPv6 } }
        val excludedAddrs4 = excludedAddrs
            .filter { it.isIPv4 }
            .map { it as IPv4Address }

        val allIps = IPv4AddressSeqRange(
            IPv4Address(0),
            IPv4Address(byteArrayOf(255.toByte(), 255.toByte(), 255.toByte(), 255.toByte()))
        )
        var ranges = excludedAddrs4.fold(listOf(allIps), ::removeIpFromRanges)
        var allowedIps4 = ranges.flatMap { it.spanWithPrefixBlocks().toList() }
        if (allowedIps4.any { it.toCanonicalString().startsWith("127.") }) {
            // Allowed IPs cannot include anything starting with 127., otherwise VpnService.Builder.addRoute()
            // is going to throw an exception. To circumvent this, exclude 127.0.0.0/8 too.
            val loopbackAddress = IPAddressString("127.0.0.0/8").address as IPv4Address
            ranges = removeIpFromRanges(ranges, loopbackAddress)
            allowedIps4 = ranges.flatMap { it.spanWithPrefixBlocks().toList() }
        }
        return allowedIps4
    }

    private class SplitTunnelAppsWgConfigurator(private val builder: Interface.Builder) : SplitTunnelAppsConfigurator {
        override fun includeApplications(packageNames: List<String>) {
            builder.includeApplications(packageNames)
        }

        override fun excludeApplications(packageNames: List<String>) {
            builder.excludeApplications(packageNames)
        }
    }

    private fun Interface.Builder.splitTunnelingApps(
        connectIntent: AnyConnectIntent,
        myPackageName: String,
        splitTunneling: SplitTunnelingSettings
    ): Interface.Builder {
        val configurator = SplitTunnelAppsWgConfigurator(this)
        applyAppsSplitTunneling(configurator, connectIntent, myPackageName, splitTunneling)
        return this
    }
}
