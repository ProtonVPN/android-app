/*
 * Copyright (c) 2025. Proton AG
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

import android.content.Context
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.SplitTunnelAppsConfigurator
import com.protonvpn.android.models.vpn.applyAppsSplitTunneling
import com.protonvpn.android.models.vpn.usecase.ComputeAllowedIPs
import com.protonvpn.android.models.vpn.usecase.toIPAddress
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.servers.Server
import com.protonvpn.android.servers.api.ConnectingDomain
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.vpn.CertificateRepository
import inet.ipaddr.IPAddress
import me.proton.core.network.domain.session.SessionId
import me.proton.vpn.sdk.api.InitialConfig
import me.proton.vpn.sdk.api.InterfaceConfig
import me.proton.vpn.sdk.api.IpNetworkPrefix
import me.proton.vpn.sdk.api.Peer
import me.proton.vpn.sdk.api.SplitTunnelAppsConfig
import me.proton.vpn.sdk.api.SplitTunnelMode

class ConnectionParamsProTun(
    connectIntent: AnyConnectIntent,
    server: Server,
    connectingDomain: ConnectingDomain,
    private val peers: List<Peer>,
    ipv6SettingEnabled: Boolean,
) : ConnectionParams(
    connectIntent,
    server,
    connectingDomain,
    VpnProtocol.WireGuard,
    null,
    null,
    null,
    ipv6SettingEnabled = ipv6SettingEnabled,
), java.io.Serializable {

    override val info get() = "IP: ${connectingDomain?.entryDomain} Server: ${server.serverName}\n" +
            peers.info()

    @Throws(IllegalStateException::class)
    suspend fun getTunnelConfig(
        context: Context,
        userSettings: LocalUserSettings,
        sessionId: SessionId?,
        certificateRepository: CertificateRepository,
        computeAllowedIPs: ComputeAllowedIPs,
    ): InitialConfig {
        val allowedIps =
            if (connectIntent is AnyConnectIntent.GuestHole) {
                listOf("0.0.0.0/0".toIPAddress(), "::/0".toIPAddress())
            } else {
                computeAllowedIPs(userSettings)
            }

        val iface = InterfaceConfig(
            supportInTunnelIPv6 = enableIPv6 && server.isIPv6Supported,
            customDns = userSettings.customDns.effectiveDnsList,
            routes = allowedIps.map { it.toIpNetworkPrefix() },
            splitTunnelAppsConfig = splitTunnelAppsConfig(context.packageName, userSettings),
        )

        val privateKey = certificateRepository.getX25519Key(sessionId)
        return InitialConfig(iface, privateKey, peers)
    }

    fun splitTunnelAppsConfig(
        myPackageName: String,
        userSettings: LocalUserSettings,
    ): SplitTunnelAppsConfig? {
        val configurator = SplitTunnelAppsProTunConfigurator()
        applyAppsSplitTunneling(
            configurator,
            connectIntent,
            myPackageName,
            userSettings.splitTunneling,
            userSettings.lanConnectionsAllowDirect
        )
        return configurator.buildConfig()
    }

    private class SplitTunnelAppsProTunConfigurator : SplitTunnelAppsConfigurator {

        var included = emptyList<String>()
        var excluded = emptyList<String>()

        override fun includeApplications(packageNames: List<String>) {
            included += packageNames
        }

        override fun excludeApplications(packageNames: List<String>) {
            excluded += packageNames
        }

        fun buildConfig() : SplitTunnelAppsConfig? = when {
            included.isNotEmpty() ->
                SplitTunnelAppsConfig(SplitTunnelMode.Include, included)
            excluded.isNotEmpty() ->
                SplitTunnelAppsConfig(SplitTunnelMode.Exclude,excluded)
            else ->
                null
        }
    }

    private fun IPAddress.toIpNetworkPrefix() =
        IpNetworkPrefix(toInetAddress(), prefixLength)
}

private fun List<Peer>.info(): String {
    return this.joinToString(separator = "") { peer ->
        val portsInfo = peer.ports.entries.joinToString(separator = "\n") { (protocol, ports) ->
            "\t\t$protocol -> ${ports.joinToString(", ")}"
        }
        "\t${peer.address}:\n$portsInfo\n"
    }
}