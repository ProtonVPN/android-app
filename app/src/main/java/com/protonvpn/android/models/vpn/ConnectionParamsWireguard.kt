/*
 * Copyright (c) 2021 Proton AG
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
import com.protonvpn.android.models.vpn.usecase.ComputeAllowedIPs
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.settings.data.SplitTunnelingSettings
import com.protonvpn.android.utils.Constants.VPN_CLIENT_IP
import com.protonvpn.android.utils.Constants.VPN_CLIENT_IP_V6
import com.protonvpn.android.utils.Constants.VPN_SERVER_IP
import com.protonvpn.android.utils.Constants.VPN_SERVER_IP_V6
import com.protonvpn.android.vpn.CertificateRepository
import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import me.proton.core.network.domain.session.SessionId

class ConnectionParamsWireguard(
    connectIntent: AnyConnectIntent,
    server: Server,
    port: Int,
    connectingDomain: ConnectingDomain,
    entryIp: String?,
    transmission: TransmissionProtocol,
    ipv6SettingEnabled: Boolean,
) : ConnectionParams(
    connectIntent,
    server,
    connectingDomain,
    VpnProtocol.WireGuard,
    entryIp,
    port,
    transmission,
    ipv6SettingEnabled = ipv6SettingEnabled,
), java.io.Serializable {

    override val info get() = "${super.info} $transmissionProtocol port: $port"

    @Throws(IllegalStateException::class)
    suspend fun getTunnelConfig(
        context: Context,
        userSettings: LocalUserSettings,
        sessionId: SessionId?,
        certificateRepository: CertificateRepository,
        computeAllowedIPs: ComputeAllowedIPs,
    ): Config =
        getTunnelConfig(
            context.packageName,
            userSettings,
            sessionId,
            certificateRepository,
            computeAllowedIPs
        )

    @Throws(IllegalStateException::class)
    @VisibleForTesting
    suspend fun getTunnelConfig(
        myPackageName: String,
        userSettings: LocalUserSettings,
        sessionId: SessionId?,
        certificateRepository: CertificateRepository,
        computeAllowedIPs: ComputeAllowedIPs,
    ): Config {
        val entryIp = entryIp ?: requireNotNull(connectingDomain?.getEntryIp(protocolSelection))

        val allowedIpsString =
            if (connectIntent is AnyConnectIntent.GuestHole) {
                "0.0.0.0/0, ::/0"
            } else {
                computeAllowedIPs(userSettings).joinToString(", ") { it.toCanonicalString() }
            }

        val peer = Peer.Builder()
            .parsePublicKey(requireNotNull(connectingDomain?.publicKeyX25519))
            .parseEndpoint("$entryIp:$port")
            .parseAllowedIPs(allowedIpsString)
            // Having persistent keepalive ensures that WG handshake will be triggered even if no data goes through
            // the tunnel - which fixes the issue on some devices where handshake is not triggered and we're stuck in
            // connecting state.
            .setPersistentKeepalive(60)
            .build()

        val (addresses, dns) = if (enableIPv6 == true && server.isIPv6Supported) {
            ProtonLogger.logCustom(LogCategory.CONN, "WireGuard IPv4+6 tunnel")
            "$VPN_CLIENT_IP/32, $VPN_CLIENT_IP_V6/128" to "$VPN_SERVER_IP, $VPN_SERVER_IP_V6"
        } else {
            ProtonLogger.logCustom(LogCategory.CONN, "WireGuard IPv4 tunnel")
            "$VPN_CLIENT_IP/32" to VPN_SERVER_IP
        }
        val splitTunneling = userSettings.splitTunneling
        val dnsServers: String = buildList {
            addAll(userSettings.customDns.effectiveDnsList)
            add(dns)
        }.joinToString(",")
        val iface = Interface.Builder()
            .parseAddresses(addresses)
            .parseDnsServers(dnsServers.trim())
            .parsePrivateKey(certificateRepository.getX25519Key(sessionId))
            .splitTunnelingApps(connectIntent, myPackageName, splitTunneling, userSettings.lanConnectionsAllowDirect)
            .build()

        return Config.Builder().addPeer(peer).setInterface(iface).build()
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
        splitTunneling: SplitTunnelingSettings,
        allowDirectLanConnections: Boolean
    ): Interface.Builder {
        val configurator = SplitTunnelAppsWgConfigurator(this)
        applyAppsSplitTunneling(configurator, connectIntent, myPackageName, splitTunneling, allowDirectLanConnections)
        return this
    }
}
