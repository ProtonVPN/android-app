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
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.wireguard.ConfigProxy
import com.protonvpn.android.utils.ProtonLogger
import com.protonvpn.android.vpn.CertificateRepository
import com.wireguard.config.Config
import de.blinkt.openvpn.core.NetworkUtils
import org.strongswan.android.utils.IPRange
import org.strongswan.android.utils.IPRangeSet

class ConnectionParamsWireguard(
    profile: Profile,
    server: Server,
    connectingDomain: ConnectingDomain
) : ConnectionParams(
    profile,
    server,
    connectingDomain,
    VpnProtocol.WireGuard
), java.io.Serializable {

    @Throws(IllegalStateException::class)
    suspend fun getTunnelConfig(
        context: Context,
        userData: UserData,
        certificateRepository: CertificateRepository
    ): Config {
        if (connectingDomain?.publicKeyX25519 == null) {
            throw IllegalStateException("Null server public key. Cannot connect to wireguard")
        }

        val config = ConfigProxy()

        config.interfaceProxy.addresses = "10.2.0.2/32"
        config.interfaceProxy.dnsServers = "10.2.0.1"
        config.interfaceProxy.privateKey = certificateRepository.getX25519Key(userData.sessionId!!)

        val peerProxy = config.addPeer()
        peerProxy.publicKey = connectingDomain.publicKeyX25519
        peerProxy.endpoint = connectingDomain.entryIp + ":" + WIREGUARD_PORT

        val excludedIPs = mutableListOf<String>()
        if (userData.useSplitTunneling) {
            userData.splitTunnelIpAddresses.takeIf { it.isNotEmpty() }?.let {
                excludedIPs += it
            }
            userData.splitTunnelApps?.takeIf { it.isNotEmpty() }?.let {
                config.interfaceProxy.excludedApplications = it.toSortedSet()
            }
        }
        if (userData.bypassLocalTraffic())
            excludedIPs += NetworkUtils.getLocalNetworks(context, false).toList()

        val allowedIps = calculateAllowedIps(excludedIPs)
        ProtonLogger.log("Allowed IPs: " + allowedIps)
        peerProxy.allowedIps = allowedIps

        return config.resolve()
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun calculateAllowedIps(excludedIps: List<String>): String {
        val ipRangeSet = IPRangeSet.fromString("0.0.0.0/0")
        excludedIps.forEach {
            ipRangeSet.remove(IPRange(it))
        }

        // IPRangeSet class does not support IPv6 CIDR so we need to add them here
        // explicitly to not leak IPv6 for Wireguard then split tunneling is used
        return ipRangeSet.subnets().joinToString(", ") + ", ::/0"
    }

    companion object {

        private const val WIREGUARD_PORT = "51820"
    }
}
