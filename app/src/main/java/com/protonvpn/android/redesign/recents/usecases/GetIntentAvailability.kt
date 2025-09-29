/*
 * Copyright (c) 2024. Proton AG
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
package com.protonvpn.android.redesign.recents.usecases

import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.data.hasAccessToServer
import com.protonvpn.android.servers.Server
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentAvailability
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.vpn.ProtocolSelection
import dagger.Reusable
import javax.inject.Inject

@Reusable
class GetIntentAvailability @Inject constructor(
    private val serverManager: ServerManager2,
    private val supportsProtocol: SupportsProtocol,
) {
    // Note: this is a suspending function being called in a loop which makes it potentially slow.
    suspend operator fun invoke(
        connectIntent: ConnectIntent,
        vpnUser: VpnUser?,
        settingsProtocol: ProtocolSelection
    ): ConnectIntentAvailability {
        val protocol = connectIntent.settingsOverrides?.protocol ?: settingsProtocol
        return serverManager.forConnectIntent(connectIntent, ConnectIntentAvailability.NO_SERVERS) { servers ->
            servers.getAvailability(vpnUser, protocol)
        }
    }

    private fun Iterable<Server>.getAvailability(
        vpnUser: VpnUser?,
        protocol: ProtocolSelection
    ): ConnectIntentAvailability {
        fun Server.hasAvailability(availability: ConnectIntentAvailability) = when (availability) {
            ConnectIntentAvailability.NO_SERVERS, ConnectIntentAvailability.UNAVAILABLE_PLAN -> true
            ConnectIntentAvailability.UNAVAILABLE_PROTOCOL -> vpnUser.hasAccessToServer(this)
            ConnectIntentAvailability.AVAILABLE_OFFLINE -> supportsProtocol(this, protocol)
            ConnectIntentAvailability.ONLINE -> online
        }

        return maxOfOrNull { server ->
            ConnectIntentAvailability.entries.toTypedArray()
                .takeWhile { server.hasAvailability(it) }
                .last()
                .also {
                    // The list of servers may be long and most of them should be online. Finish the loop early.
                    if (it == ConnectIntentAvailability.ONLINE) return@getAvailability ConnectIntentAvailability.ONLINE
                }
        } ?: ConnectIntentAvailability.NO_SERVERS
    }
}
