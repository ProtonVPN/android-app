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

import com.protonvpn.android.R
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.redesign.recents.data.DefaultConnection
import com.protonvpn.android.redesign.recents.data.RecentConnection
import com.protonvpn.android.redesign.recents.data.getRecentIdOrNull
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentAvailability
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentViewState
import com.protonvpn.android.redesign.vpn.ui.GetConnectIntentViewState
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.flatMapLatestNotNull
import com.protonvpn.android.vpn.ProtocolSelection
import dagger.Reusable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import me.proton.core.presentation.R as CoreR

data class DefaultConnectionViewState(
    val recents: List<DefaultConnItem>,
)

sealed class DefaultConnItem {
    data class DefaultConnItemViewState(
        val id: Long,
        val connectIntent: ConnectIntent,
        val connectIntentViewState: ConnectIntentViewState,
        val isDefaultConnection: Boolean,
    ) : DefaultConnItem()

    data class HeaderSeparator(val titleRes: Int) : DefaultConnItem()

    sealed class PreDefinedItem(
        open val id: Long,
        open val titleRes: Int,
        open val subtitleRes: Int,
        open val iconRes: Int,
        open val isDefaultConnection: Boolean
    ) : DefaultConnItem()

    data class FastestItem(
        override val id: Long = ID,
        override val titleRes: Int,
        override val subtitleRes: Int,
        override val iconRes: Int,
        override val isDefaultConnection: Boolean
    ) : PreDefinedItem(id, titleRes, subtitleRes, iconRes, isDefaultConnection) {
        companion object {
            const val ID = -1L
        }
    }

    data class MostRecentItem(
        override val id: Long = ID,
        override val titleRes: Int,
        override val subtitleRes: Int,
        override val iconRes: Int,
        override val isDefaultConnection: Boolean
    ) : PreDefinedItem(id, titleRes, subtitleRes, iconRes, isDefaultConnection) {
        companion object {
            const val ID = -2L
        }
    }
}

@Reusable
class DefaultConnectionViewStateFlow @Inject constructor(
    effectiveCurrentUserSettings: EffectiveCurrentUserSettings,
    observeDefaultConnection: ObserveDefaultConnection,
    recentsManager: RecentsManager,
    private val getConnectIntentViewState: GetConnectIntentViewState,
    private val getIntentAvailability: GetIntentAvailability,
    private val serverManager: ServerManager2,
    currentUser: CurrentUser
) : Flow<DefaultConnectionViewState> {

    private val viewState: Flow<DefaultConnectionViewState> = combine(
        currentUser.vpnUserFlow,
        effectiveCurrentUserSettings.protocol,
    ) { vpnUser, settingsProtocol ->
        vpnUser?.let { user -> Pair(user, settingsProtocol) }
    }.flatMapLatestNotNull { (vpnUser, settingsProtocol) ->
        if (vpnUser.isFreeUser) {
            val recentsViewState = createRecentsViewState(
                recents = emptyList(),
                vpnUser = vpnUser,
                defaultConnection = Constants.DEFAULT_CONNECTION,
                settingsProtocol = settingsProtocol,
            )

            flowOf(DefaultConnectionViewState(recents = recentsViewState)).filterNotNull()
        } else {
            combine(
                serverManager.serverListVersion,
                recentsManager.getRecentsList(),
                observeDefaultConnection(),
            ) { _, recents, defaultConnection ->
                val recentsViewState = createRecentsViewState(
                    recents = recents,
                    vpnUser = vpnUser,
                    defaultConnection = defaultConnection,
                    settingsProtocol = settingsProtocol,
                )

                DefaultConnectionViewState(recents = recentsViewState)
            }
        }
    }

    override suspend fun collect(collector: FlowCollector<DefaultConnectionViewState>) =
        viewState.collect(collector)

    private suspend fun createRecentsViewState(
        recents: List<RecentConnection>,
        vpnUser: VpnUser?,
        defaultConnection: DefaultConnection,
        settingsProtocol: ProtocolSelection,
    ): List<DefaultConnItem> = buildList {
        if (hasServersFor(ConnectIntent.Fastest, vpnUser, settingsProtocol)) {
            val fastestItem = DefaultConnItem.FastestItem(
                titleRes = R.string.fastest_country,
                subtitleRes = R.string.fastest_country_description,
                iconRes = CoreR.drawable.ic_proton_bolt,
                isDefaultConnection = defaultConnection is DefaultConnection.FastestConnection,
            )

            add(fastestItem)
        }

        val mostRecentConnectionItem = DefaultConnItem.MostRecentItem(
            titleRes = R.string.settings_last_connection_title,
            subtitleRes = R.string.settings_last_connection_description,
            iconRes = CoreR.drawable.ic_proton_clock_rotate_left,
            isDefaultConnection = defaultConnection is DefaultConnection.LastConnection,
        )

        add(mostRecentConnectionItem)

        val defaultRecentConnectionItems = recents
            .filter { recent ->
                hasServersFor(
                    connectIntent = recent.connectIntent,
                    vpnUser = vpnUser,
                    settingsProtocol = settingsProtocol,
                )
            }
            .map { recentConnection ->
                mapToRecentItemViewState(
                    recentConnection = recentConnection,
                    vpnUser = vpnUser,
                    isDefaultConnection = defaultConnection.getRecentIdOrNull() == recentConnection.id,
                )
            }
            .filterNot { defaultConnItemViewState ->
                defaultConnItemViewState.connectIntent == ConnectIntent.Fastest
            }

        if (defaultRecentConnectionItems.isNotEmpty()) {
            val recentsTitle = DefaultConnItem.HeaderSeparator(R.string.recents_headline)
            add(recentsTitle)

            addAll(defaultRecentConnectionItems)
        }
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

    private suspend fun mapToRecentItemViewState(
        recentConnection: RecentConnection,
        vpnUser: VpnUser?,
        isDefaultConnection: Boolean,
    ): DefaultConnItem.DefaultConnItemViewState = with(recentConnection) {
        DefaultConnItem.DefaultConnItemViewState(
            id = id,
            isDefaultConnection = isDefaultConnection,
            connectIntent = connectIntent,
            connectIntentViewState = getConnectIntentViewState.forRecent(
                recentConnection = recentConnection,
                isFreeUser = vpnUser?.isFreeUser == true,
            )
        )
    }

}
