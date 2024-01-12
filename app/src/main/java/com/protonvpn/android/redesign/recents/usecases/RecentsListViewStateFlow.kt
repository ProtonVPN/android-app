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

import androidx.annotation.StringRes
import com.protonvpn.android.R
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.data.hasAccessToServer
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.redesign.recents.data.RecentConnection
import com.protonvpn.android.redesign.recents.ui.RecentAvailability
import com.protonvpn.android.redesign.recents.ui.RecentItemViewState
import com.protonvpn.android.redesign.recents.ui.VpnConnectionCardViewState
import com.protonvpn.android.redesign.recents.ui.VpnConnectionState
import com.protonvpn.android.redesign.vpn.ChangeServerManager
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentViewState
import com.protonvpn.android.redesign.vpn.ui.GetConnectIntentViewState
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.utils.flatMapLatestNotNull
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.android.vpn.isConnectedOrConnecting
import dagger.Reusable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
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
    private val serverManager: ServerManager2,
    private val supportsProtocol: SupportsProtocol,
    private val userSettings: EffectiveCurrentUserSettings,
    vpnStatusProvider: VpnStatusProviderUI,
    changeServerManager: ChangeServerManager,
    currentUser: CurrentUser
): Flow<RecentsListViewState> {
    // Used on clean installations.
    private val defaultConnectIntent = ConnectIntent.Default

    private data class RecentsData(
        val user: VpnUser, val recents: List<RecentConnection>, val mostRecentConnection: RecentConnection?
    )

    private val dataFlow =
        currentUser.vpnUserFlow.flatMapLatestNotNull { vpnUser ->
            if (vpnUser.isFreeUser) {
                // Don't fetch recents data for free users.
                flowOf(RecentsData(vpnUser, emptyList(), null))
            } else {
                combine(
                    recentsManager.getRecentsList(),
                    recentsManager.getMostRecentConnection(),
                ) { recents, mostRecentConnection ->
                    RecentsData(vpnUser, recents, mostRecentConnection)
                }
            }
        }

    private val viewState: Flow<RecentsListViewState> =
        dataFlow.flatMapLatestNotNull { data ->
            val (vpnUser, recents, mostRecent) = data
            combine(
                vpnStatusProvider.uiStatus,
                changeServerManager.isChangingServer,
                serverManager.serverListVersion, // Update whenever servers change.
                userSettings.protocol,
            ) { status, isChangingServer, _, protocol ->
            val connectedIntent = status.connectIntent?.takeIf { status.state.isConnectedOrConnecting() }
                val mostRecentAvailableIntent =  mostRecent?.connectIntent?.takeIf {
                    getAvailability(it, vpnUser, protocol) == RecentAvailability.ONLINE
                }
                val connectionCardIntent = connectedIntent ?: mostRecentAvailableIntent ?: defaultConnectIntent
                val connectIntentViewState = getConnectIntentViewState(
                    connectionCardIntent,
                    vpnUser.isFreeUser,
                    connectedServer = if (status.state == VpnState.Connected) status.connectionParams?.server else null
                )
                RecentsListViewState(
                    createCardState(
                        status.state,
                        isChangingServer = isChangingServer,
                        isDefaultConnection = connectedIntent === defaultConnectIntent,
                        connectionCardIntentViewState = connectIntentViewState,
                        showFreeCountriesInformationPanel = vpnUser.isFreeUser && status.state == VpnState.Disabled,
                    ),
                    createRecentsViewState(recents, connectedIntent, connectionCardIntent, vpnUser, protocol),
                    recents.find { it.connectIntent == connectionCardIntent }?.id
                )
            }
        }

    override suspend fun collect(collector: FlowCollector<RecentsListViewState>) =
        viewState.collect(collector)

    private suspend fun createRecentsViewState(
        recents: List<RecentConnection>,
        connectedIntent: ConnectIntent?,
        connectionCardIntent: ConnectIntent,
        vpnUser: VpnUser?,
        protocol: ProtocolSelection
    ): List<RecentItemViewState> =
        // Note: the loop below calls suspending functions in each iteration making it potentially slow.
        // With the legacy ServerManager this shouldn't be an issue but once we move to a different server storage this
        // code needs to be revised and all the necessary information should be fetched once in a batch instead of
        // querying one by one for each intent.
        recents.mapNotNull { recentConnection ->
            if (recentConnection.connectIntent != connectionCardIntent || recentConnection.isPinned) {
                mapToRecentItemViewState(recentConnection, connectedIntent, vpnUser, protocol)
            } else {
                null
            }
        }

    private suspend fun mapToRecentItemViewState(
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
                connectIntent = getConnectIntentViewState(connectIntent, vpnUser?.isFreeUser == true)
            )
        }

    private fun createCardState(
        vpnState: VpnState,
        isChangingServer: Boolean,
        isDefaultConnection: Boolean,
        connectionCardIntentViewState: ConnectIntentViewState,
        showFreeCountriesInformationPanel: Boolean,
    ): VpnConnectionCardViewState {
        @StringRes
        val buttonLabelRes: Int = when {
            vpnState.isEstablishingConnection && isChangingServer -> R.string.disconnect
            vpnState.isEstablishingConnection -> R.string.cancel
            vpnState is VpnState.Connected -> R.string.disconnect
            else -> R.string.connect
        }
        val cardLabelRes = when {
            vpnState is VpnState.Connected -> R.string.connection_card_label_connected
            vpnState.isEstablishingConnection && isChangingServer -> R.string.connection_card_label_changing_server
            vpnState.isEstablishingConnection -> R.string.connection_card_label_connecting
            else -> if (isDefaultConnection) {
                R.string.connection_card_label_recommended
            } else {
                R.string.connection_card_label_last_connected
            }
        }
        return VpnConnectionCardViewState(
            connectIntentViewState = connectionCardIntentViewState,
            cardLabelRes = cardLabelRes,
            mainButtonLabelRes = buttonLabelRes,
            canOpenConnectionPanel = vpnState is VpnState.Connected,
            canOpenFreeCountriesPanel = showFreeCountriesInformationPanel,
            isConnectedOrConnecting = vpnState.isConnectedOrConnecting(),
        )
    }

    // Note: this is a suspending function being called in a loop which makes it potentially slow.
    // See RecentListViewStateFlow.createRecentsViewState
    private suspend fun getAvailability(
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
            onFastestInGroup = { serverGroup, _ -> serverGroup.serverList.getAvailability(vpnUser, protocol) },
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
