/*
 * Copyright (c) 2023. Proton AG
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

import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.servers.Server
import com.protonvpn.android.servers.ServerManager2
import dagger.Reusable
import javax.inject.Inject

// Get servers that are compatible with the given intent sorted by score. Compatibility here means
// that all constraints of an intent (country, gateway name, features, etc.) are satisfied by a given
// server. However for "fastest" constraint we don't return a single fastest server but a list
// (sorted by score) instead.
@Reusable
class GetOnlineServersForIntent @Inject constructor(
    val serverManager2: ServerManager2,
    private val supportsProtocol: SupportsProtocol,
) {
    suspend operator fun invoke(
        intent: ConnectIntent,
        protocolOverride: ProtocolSelection,
        maxTier: Int,
    ): List<Server> {
        val intentServers = serverManager2.forConnectIntent(intent, emptySequence()) { servers ->
            servers.sortedBy { it.score }.asSequence()
        }
        return intentServers
            .filter { it.tier <= maxTier && it.online && supportsProtocol(it, protocolOverride) }
            .toList()
    }
}
