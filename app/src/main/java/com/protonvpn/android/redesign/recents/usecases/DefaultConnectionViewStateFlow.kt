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
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentViewState
import com.protonvpn.android.redesign.vpn.ui.GetConnectIntentViewState
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.flatMapLatestNotNull
import dagger.Reusable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.combine
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
    recentsManager: RecentsManager,
    private val getConnectIntentViewState: GetConnectIntentViewState,
    private val serverManager: ServerManager2,
    currentUser: CurrentUser
) : Flow<DefaultConnectionViewState> {

    private val viewState: Flow<DefaultConnectionViewState> =
        currentUser.vpnUserFlow.flatMapLatestNotNull { vpnUser ->
            if (vpnUser.isFreeUser) {
                flowOf(DefaultConnectionViewState(createRecentsViewState(emptyList(), vpnUser, Constants.DEFAULT_CONNECTION)))
            } else {
                combine(
                    recentsManager.getRecentsList(),
                    serverManager.serverListVersion,
                    recentsManager.getDefaultConnectionFlow()
                ) { recents, _, defaultConnection ->
                    DefaultConnectionViewState(
                        createRecentsViewState(
                            recents,
                            vpnUser,
                            defaultConnection
                        ),
                    )
                }
            }
        }

    override suspend fun collect(collector: FlowCollector<DefaultConnectionViewState>) =
        viewState.collect(collector)

    private suspend fun createRecentsViewState(
        recents: List<RecentConnection>,
        vpnUser: VpnUser?,
        defaultConnection: DefaultConnection,
    ): List<DefaultConnItem> {
        val mappedItems = recents.map { recentConnection ->
            mapToRecentItemViewState(
                recentConnection,
                vpnUser,
                defaultConnection.getRecentIdOrNull() == recentConnection.id,
            )
        }
        val mostRecentConnection = DefaultConnItem.MostRecentItem(
            titleRes = R.string.settings_last_connection_title,
            subtitleRes = R.string.settings_last_connection_description,
            iconRes = CoreR.drawable.ic_proton_clock_rotate_left,
            isDefaultConnection = defaultConnection is DefaultConnection.LastConnection,
        )
        val recentsTitle = DefaultConnItem.HeaderSeparator(R.string.recents_headline)

        val fastestItem = DefaultConnItem.FastestItem(
            titleRes = R.string.fastest_country,
            subtitleRes = R.string.fastest_country_description,
            iconRes = CoreR.drawable.ic_proton_bolt,
            isDefaultConnection = defaultConnection is DefaultConnection.FastestConnection
        )
        return if (mappedItems.isEmpty()) {
            listOf(fastestItem, mostRecentConnection)
        } else {
            val adjustedList = mappedItems.filterNot { it.connectIntent == ConnectIntent.Fastest }
            listOf(fastestItem, mostRecentConnection, recentsTitle) + adjustedList
        }
    }

    private suspend fun mapToRecentItemViewState(
        recentConnection: RecentConnection,
        vpnUser: VpnUser?,
        isDefaultConnection: Boolean,
    ): DefaultConnItem.DefaultConnItemViewState =
        with(recentConnection) {
            DefaultConnItem.DefaultConnItemViewState(
                id = id,
                isDefaultConnection = isDefaultConnection,
                connectIntent = connectIntent,
                connectIntentViewState = getConnectIntentViewState(
                    recentConnection,
                    vpnUser?.isFreeUser == true
                )
            )
        }
}
