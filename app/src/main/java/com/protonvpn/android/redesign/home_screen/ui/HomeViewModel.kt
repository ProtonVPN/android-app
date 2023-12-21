/*
 * Copyright (c) 2023 Proton AG
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
package com.protonvpn.android.redesign.home_screen.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.R
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.recents.data.RecentConnection
import com.protonvpn.android.redesign.recents.ui.RecentAvailability
import com.protonvpn.android.redesign.recents.ui.RecentItemViewState
import com.protonvpn.android.redesign.recents.ui.VpnConnectionCardViewState
import com.protonvpn.android.redesign.recents.ui.VpnConnectionState
import com.protonvpn.android.redesign.recents.usecases.GetQuickConnectIntent
import com.protonvpn.android.redesign.recents.usecases.RecentsListViewState
import com.protonvpn.android.redesign.recents.usecases.RecentsListViewStateFlow
import com.protonvpn.android.redesign.recents.usecases.RecentsManager
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentPrimaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentViewState
import com.protonvpn.android.redesign.vpn.ui.VpnStatusViewState
import com.protonvpn.android.redesign.vpn.ui.VpnStatusViewStateFlow
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.DisconnectTrigger
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnUiDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import me.proton.core.presentation.savedstate.state
import javax.inject.Inject

private const val TriggerDescription = "Home screen"
private const val DialogStateKey = "dialog"

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    recentsListViewStateFlow: RecentsListViewStateFlow,
    vpnStatusViewStateFlow: VpnStatusViewStateFlow,
    private val recentsManager: RecentsManager,
    private val vpnConnectionManager: VpnConnectionManager,
    private val quickConnectIntent: GetQuickConnectIntent
) : ViewModel() {

    private val initialCardViewState =
        VpnConnectionCardViewState(
            R.string.connection_card_label_recommended,
            ConnectIntentViewState(
                ConnectIntentPrimaryLabel.Country(CountryId.fastest, null),
                null,
                emptySet()
            ),
            VpnConnectionState.Disconnected
        )
    val recentsViewState = recentsListViewStateFlow
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(),
            RecentsListViewState(initialCardViewState, emptyList(), null)
        )

    val vpnStateViewFlow: StateFlow<VpnStatusViewState> = vpnStatusViewStateFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = VpnStatusViewState.Disabled()
    )

    enum class DialogState {
        CountryInMaintenance, CityInMaintenance, ServerInMaintenance, GatewayInMaintenance, ServerNotAvailable
    }

    private var dialogState by savedStateHandle.state<DialogState?>(null, DialogStateKey)
    val dialogStateFlow = savedStateHandle.getStateFlow<DialogState?>(DialogStateKey, null)

    val eventNavigateToUpgrade = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun connect(vpnUiDelegate: VpnUiDelegate, connectIntent: AnyConnectIntent) {
        vpnConnectionManager.connect(
            vpnUiDelegate,
            connectIntent,
            ConnectTrigger.QuickConnect(TriggerDescription)
        )
    }

    suspend fun connect(vpnUiDelegate: VpnUiDelegate) {
        connect(vpnUiDelegate, quickConnectIntent())
    }

    suspend fun onRecentClicked(item: RecentItemViewState, vpnUiDelegate: VpnUiDelegate) {
        val recent = recentsManager.getRecentById(item.id)
        if (recent != null) {
            when (item.availability) {
                RecentAvailability.UNAVAILABLE_PLAN -> eventNavigateToUpgrade.tryEmit(Unit)
                RecentAvailability.UNAVAILABLE_PROTOCOL -> dialogState = DialogState.ServerNotAvailable
                RecentAvailability.AVAILABLE_OFFLINE -> dialogState = recent.toMaintenanceDialogType()
                RecentAvailability.ONLINE -> vpnConnectionManager.connect(
                    vpnUiDelegate,
                    recent.connectIntent,
                    if (recent.isPinned) ConnectTrigger.RecentPinned else ConnectTrigger.RecentRegular
                )
            }
        }
    }

    fun disconnect() {
        vpnConnectionManager.disconnect(DisconnectTrigger.QuickConnect(TriggerDescription))
    }

    fun dismissDialog() {
        dialogState = null
    }

    fun togglePinned(item: RecentItemViewState) {
        if (item.isPinned) {
            recentsManager.unpin(item.id)
        } else {
            recentsManager.pin(item.id)
        }
    }

    fun removeRecent(item: RecentItemViewState) {
        recentsManager.remove(item.id)
    }

    private fun RecentConnection.toMaintenanceDialogType() =
        when (connectIntent) {
            is ConnectIntent.FastestInCountry -> DialogState.CountryInMaintenance
            is ConnectIntent.FastestInCity -> DialogState.CityInMaintenance
            is ConnectIntent.SecureCore,
            is ConnectIntent.Server -> DialogState.ServerInMaintenance
            is ConnectIntent.Gateway ->
                if (connectIntent.serverId != null) DialogState.ServerInMaintenance
                else DialogState.GatewayInMaintenance
        }
}
