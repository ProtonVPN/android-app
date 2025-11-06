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

package com.protonvpn.android.models.vpn.usecase

import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.di.Distinct
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.servers.api.ConnectingDomain
import com.protonvpn.android.servers.Server
import com.protonvpn.android.vpn.ProtocolSelection
import javax.inject.Inject
import javax.inject.Singleton

@Distinct
class GetSmartProtocols @Inject constructor(
    val appConfig: AppConfig
) {
    operator fun invoke(): List<ProtocolSelection> = appConfig.getSmartProtocols()
}

@Singleton
class SupportsProtocol @Inject constructor(
    val getSmartProtocols: GetSmartProtocols
) {
    operator fun invoke(server: Server, protocol: ProtocolSelection) =
        server.connectingDomains.any { invoke(it, protocol) }

    operator fun invoke(server: Server, vpnProtocol: VpnProtocol): Boolean =
        if (vpnProtocol == VpnProtocol.Smart)
            invoke(server, ProtocolSelection.SMART)
        else ProtocolSelection.PROTOCOLS_FOR[vpnProtocol]?.any {
            invoke(server, it)
        } == true

    // When AppConfig changes, list needs to be re-filtered
    operator fun invoke(connectingDomain: ConnectingDomain, protocol: ProtocolSelection) =
        if (protocol.vpn == VpnProtocol.Smart)
            getSmartProtocols().any { connectingDomain.supportsRealProtocol(it) }
        else
            connectingDomain.supportsRealProtocol(protocol)

    private fun ConnectingDomain.supportsRealProtocol(protocol: ProtocolSelection) =
        getEntryIp(protocol) != null && ((protocol.vpn != VpnProtocol.WireGuard && protocol.vpn != VpnProtocol.ProTun) || !publicKeyX25519.isNullOrBlank())
}
