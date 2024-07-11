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
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.redesign.recents.data.DefaultConnection
import com.protonvpn.android.redesign.recents.data.RecentConnection
import com.protonvpn.android.redesign.recents.ui.CardLabel
import com.protonvpn.android.redesign.recents.ui.RecentAvailability
import com.protonvpn.android.redesign.recents.ui.RecentItemViewState
import com.protonvpn.android.redesign.recents.ui.VpnConnectionCardViewState
import com.protonvpn.android.redesign.vpn.ChangeServerManager
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.isCompatibleWith
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
    private val userSettings: EffectiveCurrentUserSettings,
    private val getIntentAvailability: GetIntentAvailability,
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
                recentsManager.getDefaultConnectionFlow(),
            ) { status, isChangingServer, _, protocol, defaultConnection ->
                val connectedIntent = status.connectIntent?.takeIf { status.state.isConnectedOrConnecting() }
                val connectedServer = status.server?.takeIf { status.state.isConnectedOrConnecting() }
                val mostRecentAvailableIntent =  mostRecent?.connectIntent?.takeIf {
                    getIntentAvailability(it, vpnUser, protocol) == RecentAvailability.ONLINE
                }
                val connectionCardIntent = connectedIntent
                    ?: when (defaultConnection) {
                        DefaultConnection.FastestConnection -> defaultConnectIntent
                        DefaultConnection.LastConnection -> mostRecentAvailableIntent
                        is DefaultConnection.Recent -> recents.firstOrNull { it.id == defaultConnection.recentId }?.connectIntent
                    } ?: defaultConnectIntent

                val connectIntentViewState = getConnectIntentViewState(
                    connectionCardIntent,
                    vpnUser.isFreeUser,
                    connectedServer = if (status.state == VpnState.Connected) status.connectionParams?.server else null
                )
                RecentsListViewState(
                    createCardState(
                        status.state,
                        isChangingServer = isChangingServer,
                        connectionCardIntentViewState = connectIntentViewState,
                        showFreeCountriesInformationPanel = vpnUser.isFreeUser && status.state == VpnState.Disabled,
                        isFreeUser = vpnUser.isFreeUser,
                    ),
                    createRecentsViewState(
                        recents,
                        connectedIntent,
                        connectedServer,
                        connectionCardIntent,
                        vpnUser,
                        protocol
                    ),
                    recents.find { it.connectIntent == connectionCardIntent }?.id
                )
            }
        }

    override suspend fun collect(collector: FlowCollector<RecentsListViewState>) =
        viewState.collect(collector)

    private suspend fun createRecentsViewState(
        recents: List<RecentConnection>,
        connectedIntent: ConnectIntent?,
        connectedServer: Server?,
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
                mapToRecentItemViewState(recentConnection, connectedIntent, connectedServer, vpnUser, protocol)
            } else {
                null
            }
        }

    private suspend fun mapToRecentItemViewState(
        recentConnection: RecentConnection,
        connectedIntent: ConnectIntent?,
        connectedServer: Server?,
        vpnUser: VpnUser?,
        protocol: ProtocolSelection
    ): RecentItemViewState =
        with (recentConnection) {
            RecentItemViewState(
                id = id,
                isPinned = isPinned,
                isConnected = connectedIntent == connectIntent &&
                    connectedServer.isCompatibleWith(connectIntent, matchFastest = true),
                availability = getIntentAvailability(connectIntent, vpnUser, protocol),
                connectIntent = getConnectIntentViewState(connectIntent, vpnUser?.isFreeUser == true)
            )
        }

    private fun createCardState(
        vpnState: VpnState,
        isChangingServer: Boolean,
        connectionCardIntentViewState: ConnectIntentViewState,
        showFreeCountriesInformationPanel: Boolean,
        isFreeUser: Boolean,
    ): VpnConnectionCardViewState {
        @StringRes
        val buttonLabelRes: Int = when {
            vpnState.isEstablishingConnection && isChangingServer -> R.string.disconnect
            vpnState.isEstablishingConnection -> R.string.cancel
            vpnState is VpnState.Connected -> R.string.disconnect
            else -> R.string.connect
        }
        val cardLabel: CardLabel = when {
            vpnState is VpnState.Connected -> CardLabel(R.string.connection_card_label_connected, isClickable = false)
            vpnState.isEstablishingConnection && isChangingServer -> CardLabel(R.string.connection_card_label_changing_server, isClickable = false)
            vpnState.isEstablishingConnection -> CardLabel(R.string.connection_card_label_connecting, isClickable = false)
            else -> when { // Disconnected
                isFreeUser -> CardLabel(R.string.connection_card_label_free_connection, isClickable = false)
                else -> CardLabel(R.string.connection_card_label_default_connection, isClickable = true)
            }
        }
        return VpnConnectionCardViewState(
            connectIntentViewState = connectionCardIntentViewState,
            cardLabel = cardLabel,
            mainButtonLabelRes = buttonLabelRes,
            canOpenConnectionPanel = vpnState is VpnState.Connected,
            canOpenFreeCountriesPanel = showFreeCountriesInformationPanel,
            isConnectedOrConnecting = vpnState.isConnectedOrConnecting(),
        )
    }
}
