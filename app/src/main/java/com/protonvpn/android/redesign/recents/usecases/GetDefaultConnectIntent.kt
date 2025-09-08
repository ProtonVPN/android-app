/*
 * Copyright (c) 2025 Proton AG
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
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentAvailability
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.vpn.ProtocolSelection
import dagger.Reusable
import javax.inject.Inject

@Reusable
class GetDefaultConnectIntent @Inject constructor(
    private val getIntentAvailability: GetIntentAvailability,
    private val serverManager2: ServerManager2,
) {

    suspend operator fun invoke(
        vpnUser: VpnUser?,
        protocolSelection: ProtocolSelection,
    ): ConnectIntent {
        if (hasServersFor(ConnectIntent.Default, vpnUser, protocolSelection)) {
            return ConnectIntent.Default
        }

        return serverManager2.getGateways()
            .firstOrNull { gatewayGroup ->
                hasServersFor(
                    connectIntent = ConnectIntent.Gateway(
                        gatewayName = gatewayGroup.name(),
                        serverId = null,
                    ),
                    vpnUser = vpnUser,
                    settingsProtocol = protocolSelection,
                )
            }
            ?.let { firstAvailableGatewayGroup ->
                ConnectIntent.Gateway(
                    gatewayName = firstAvailableGatewayGroup.name(),
                    serverId = null,
                )
            }
            ?: ConnectIntent.Default
    }

    suspend fun hasServersFor(
        connectIntent: ConnectIntent,
        vpnUser: VpnUser?,
        settingsProtocol: ProtocolSelection,
    ): Boolean = getIntentAvailability(
        connectIntent = connectIntent,
        vpnUser = vpnUser,
        settingsProtocol = settingsProtocol,
    ) != ConnectIntentAvailability.NO_SERVERS

}
