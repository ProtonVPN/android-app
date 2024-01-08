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

package com.protonvpn.android.redesign.recents.usecases

import com.protonvpn.android.R
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.data.hasAccessToServer
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.recents.data.RecentConnection
import com.protonvpn.android.redesign.recents.ui.RecentAvailability
import com.protonvpn.android.redesign.recents.ui.RecentItemViewState
import com.protonvpn.android.redesign.recents.ui.VpnConnectionCardViewState
import com.protonvpn.android.redesign.recents.ui.VpnConnectionState
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ui.GetConnectIntentViewState
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.flatMapLatestNotNull
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStatusProviderUI
import dagger.Reusable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

data class RecentsListViewState(
    val connectionCard: VpnConnectionCardViewState,
    val recents: List<RecentItemViewState>,
    val connectionCardRecentId: Long?
)

@Reusable
class RecentsListViewStateFlow @Inject constructor(
    recentsManager: RecentsManager,
    private val getConnectIntentViewState: GetConnectIntentViewState,
    private val serverManager: ServerManager,
    private val supportsProtocol: SupportsProtocol,
    private val userSettings: EffectiveCurrentUserSettings,
    vpnStatusProvider: VpnStatusProviderUI,
    currentUser: CurrentUser
): Flow<RecentsListViewState> {
    // Used on clean installations.
    private val defaultConnectIntent = ConnectIntent.Default

    private val viewState: Flow<RecentsListViewState> = currentUser.vpnUserFlow
        .flatMapLatestNotNull { vpnUser ->
            combine(
                recentsManager.getRecentsList(),
                recentsManager.getMostRecentConnection(),
                vpnStatusProvider.uiStatus,
                serverManager.serverListVersion, // Update whenever servers change.
                userSettings.protocol,
            ) { recents, mostRecent, status, _, protocol ->
                val connectedIntent = status.connectIntent?.takeIf {
                    status.state == VpnState.Connected || status.state.isEstablishingConnection
                }
                val mostRecentAvailableIntent =  mostRecent?.connectIntent?.takeIf {
                    getAvailability(it, vpnUser, protocol) == RecentAvailability.ONLINE
                }
                val connectionCardIntent = connectedIntent ?: mostRecentAvailableIntent ?: defaultConnectIntent
                RecentsListViewState(
                    createCardState(
                        status.state,
                        connectionCardIntent,
                        if (status.state == VpnState.Connected) status.connectionParams?.server else null
                    ),
                    createRecentsViewState(recents, connectedIntent, connectionCardIntent, vpnUser, protocol),
                    recents.find { it.connectIntent == connectionCardIntent }?.id
                )
            }
        }

    override suspend fun collect(collector: FlowCollector<RecentsListViewState>) =
        viewState.collect(collector)

    private fun createRecentsViewState(
        recents: List<RecentConnection>,
        connectedIntent: ConnectIntent?,
        connectionCardIntent: ConnectIntent,
        vpnUser: VpnUser?,
        protocol: ProtocolSelection
    ): List<RecentItemViewState> =
        recents.mapNotNull { recentConnection ->
            if (recentConnection.connectIntent != connectionCardIntent || recentConnection.isPinned) {
                mapToRecentItemViewState(recentConnection, connectedIntent, vpnUser, protocol)
            } else {
                null
            }
        }

    private fun mapToRecentItemViewState(
        recentConnection: RecentConnection,
        connectedIntent: ConnectIntent?,
        vpnUser: VpnUser?,
        protocol: ProtocolSelection
    ): RecentItemViewState =
        with (recentConnection) {
            RecentItemViewState(
                id = id,
                isPinned = isPinned,
                isConnected = connectedIntent == connectIntent,
                availability = getAvailability(connectIntent, vpnUser, protocol),
                connectIntent = getConnectIntentViewState(connectIntent)
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

    private fun getAvailability(
        connectIntent: ConnectIntent,
        vpnUser: VpnUser?,
        protocol: ProtocolSelection
    ): RecentAvailability =
        serverManager.forConnectIntent(
            connectIntent,
            onFastest = { isSecureCore ->
                if (!isSecureCore || vpnUser?.isFreeUser != true) RecentAvailability.ONLINE
                else RecentAvailability.UNAVAILABLE_PLAN
            },
            onFastestInCountry = { country, _ -> country.serverList.getAvailability(vpnUser, protocol) },
            onFastestInCity = { _, servers -> servers.getAvailability(vpnUser, protocol) },
            onServer = { server -> listOf(server).getAvailability(vpnUser, protocol) },
            fallbackResult = RecentAvailability.UNAVAILABLE_PLAN
        )

    private fun Iterable<Server>.getAvailability(vpnUser: VpnUser?, protocol: ProtocolSelection): RecentAvailability {
        fun Server.hasAvailability(availability: RecentAvailability) = when (availability) {
            RecentAvailability.UNAVAILABLE_PLAN -> true
            RecentAvailability.UNAVAILABLE_PROTOCOL -> vpnUser.hasAccessToServer(this)
            RecentAvailability.AVAILABLE_OFFLINE -> supportsProtocol(this, protocol)
            RecentAvailability.ONLINE -> online
        }

        return maxOfOrNull { server ->
            RecentAvailability.values()
                .takeWhile { server.hasAvailability(it) }
                .last()
                .also {
                    // The list of servers may be long and most of them should be online. Finish the loop early.
                    if (it == RecentAvailability.ONLINE) return@maxOfOrNull RecentAvailability.ONLINE
                }
        } ?: RecentAvailability.UNAVAILABLE_PLAN
    }
}
