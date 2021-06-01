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

import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.wireguard.ConfigProxy
import com.protonvpn.android.vpn.CertificateRepository
import com.wireguard.config.Config

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
        peerProxy.endpoint = connectingDomain.getExitIP() + ":" + WIREGUARD_PORT
        peerProxy.allowedIps = "0.0.0.0/0"

        return config.resolve()
    }

    companion object {

        private const val WIREGUARD_PORT = "51820"
    }
}
