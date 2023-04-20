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

package com.protonvpn.android.redesign.vpn.ui

import com.protonvpn.android.R
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.recents.GetMostRecentConnectIntent
import com.protonvpn.android.redesign.stubs.toConnectIntent
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStatusProviderUI
import dagger.Reusable
import kotlinx.coroutines.flow.combine
import java.util.EnumSet
import javax.inject.Inject

@Reusable
class GetVpnConnectionCardViewState @Inject constructor(
    vpnStatusProvider: VpnStatusProviderUI,
    serverManager: ServerManager,
    effectiveUserSettings: EffectiveCurrentUserSettings,
    private val getConnectIntentViewState: GetConnectIntentViewState,
    getMostRecentConnectIntent: GetMostRecentConnectIntent
) {
    // Used on clean installations.
    private val defaultConnectIntent =
        ConnectIntent.FastestInCountry(CountryId.fastest, EnumSet.noneOf(ServerFeature::class.java))

    val cardViewState = combine(
        getMostRecentConnectIntent.mostRecent,
        effectiveUserSettings.effectiveSettings,
        vpnStatusProvider.status
    ) { recent, settings, status ->
        // The VpnStateMonitor.Status contains connection params for the last connection even when disconnected.
        // Don't use these values unless connected to avoid showing stale state.
        val connectIntent =
            status.connectionParams?.profile?.toConnectIntent(serverManager, settings)
                ?.takeIf { status.state == VpnState.Connected }
                ?: recent
                ?: defaultConnectIntent
        createCardState(
            status.state,
            connectIntent,
            if (status.state == VpnState.Connected) status.connectionParams?.server else null
        )
    }

    private fun createCardState(
        vpnState: VpnState,
        connectIntent: ConnectIntent,
        connectedServer: Server?
    ): VpnConnectionCardViewState {
        val vpnConnectionState = when {
            vpnState.isEstablishingConnection -> VpnConnectionState.Connecting
            vpnState is VpnState.Connected -> VpnConnectionState.Connected
            else -> VpnConnectionState.Disconnected
        }
        val cardLabelRes = when (vpnConnectionState) {
            VpnConnectionState.Disconnected -> if (connectIntent === defaultConnectIntent) {
                R.string.connection_card_label_recommended
            } else {
                R.string.connection_card_label_last_connected
            }
            VpnConnectionState.Connecting -> R.string.connection_card_label_connecting
            VpnConnectionState.Connected -> R.string.connection_card_label_connected
        }
        return VpnConnectionCardViewState(
            connectIntentViewState = getConnectIntentViewState(connectIntent, connectedServer),
            cardLabelRes = cardLabelRes,
            connectionState = vpnConnectionState
        )
    }
}
