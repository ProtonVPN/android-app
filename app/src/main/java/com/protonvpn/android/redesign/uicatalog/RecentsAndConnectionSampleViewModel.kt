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

package com.protonvpn.android.redesign.uicatalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.R
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.recents.data.RecentsDao
import com.protonvpn.android.redesign.recents.ui.RecentItemViewState
import com.protonvpn.android.redesign.recents.usecases.RecentsListViewState
import com.protonvpn.android.redesign.recents.usecases.RecentsListViewStateFlow
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentViewState
import com.protonvpn.android.redesign.recents.ui.VpnConnectionCardViewState
import com.protonvpn.android.redesign.recents.ui.VpnConnectionState
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.DisconnectTrigger
import com.protonvpn.android.vpn.VpnConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.EnumSet
import javax.inject.Inject

// TODO: remove this. Just write automated tests and then embed the recents in home screen.
@HiltViewModel
class RecentsAndConnectionSampleViewModel @Inject constructor(
    private val mainScope: CoroutineScope,
    recentsListViewStateFlow: RecentsListViewStateFlow,
    private val recentsDao: RecentsDao,
    private val vpnConnectionManager: VpnConnectionManager,
    @WallClock private val clock: () -> Long
) : ViewModel() {

    private val initialCardViewState =
        VpnConnectionCardViewState(
            R.string.connection_card_label_recommended,
            ConnectIntentViewState(
                CountryId.fastest,
                null,
                false,
                null,
                emptySet()
            ),
            VpnConnectionState.Disconnected
        )
    val viewState = recentsListViewStateFlow
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            RecentsListViewState(initialCardViewState, emptyList(), null)
        )

    fun connect() {
        mainScope.launch {
            val connectIntent = recentsDao.getMostRecentConnection().first()?.connectIntent ?:
                ConnectIntent.FastestInCountry(CountryId.fastest, EnumSet.noneOf(ServerFeature::class.java))
            vpnConnectionManager.connectInBackground(
                connectIntent,
                ConnectTrigger.QuickConnect("UI catalog")
            )
        }
    }

    fun connectRecent(id: Long) {
        mainScope.launch {
            val recent = recentsDao.getById(id)
            if (recent != null) {
                vpnConnectionManager.connectInBackground(recent.connectIntent, ConnectTrigger.RecentRegular)
            }
        }
    }

    fun disconnect() {
        vpnConnectionManager.disconnect(DisconnectTrigger.QuickConnect("UI catalog"))
    }

    fun togglePinned(item: RecentItemViewState) = mainScope.launch {
        if (item.isPinned) {
            recentsDao.unpin(item.id)
        } else {
            recentsDao.pin(item.id, clock())
        }
    }

    fun removeRecent(item: RecentItemViewState) = mainScope.launch {
        if (item.isConnected) {
            recentsDao.unpin(item.id)
        } else {
            recentsDao.delete(item.id)
        }
    }
}
