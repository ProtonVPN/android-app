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

import com.protonvpn.android.servers.Server
import com.protonvpn.android.vpn.ProtocolSelection
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetConnectingDomain @Inject constructor(
    val supportsProtocol: SupportsProtocol
) {
    fun online(server: Server, protocol: ProtocolSelection?) =
        if (protocol == null)
            server.connectingDomains.filter { it.isOnline }
        else
            server.connectingDomains.filter { it.isOnline && supportsProtocol(it, protocol) }

    private fun supportingProtocol(server: Server, protocol: ProtocolSelection?) =
        if (protocol == null)
            server.connectingDomains
        else
            server.connectingDomains.filter { supportsProtocol(it, protocol) }

    fun random(server: Server, protocol: ProtocolSelection?) =
        online(server, protocol).randomOrNull()
            ?: supportingProtocol(server, protocol).randomOrNull()
}
