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
import com.protonvpn.android.di.ElapsedRealtimeClock
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.redesign.recents.data.RecentConnection
import com.protonvpn.android.redesign.recents.ui.RecentAvailability
import com.protonvpn.android.redesign.recents.ui.RecentItemViewState
import com.protonvpn.android.redesign.recents.usecases.GetQuickConnectIntent
import com.protonvpn.android.redesign.recents.usecases.RecentsListViewStateFlow
import com.protonvpn.android.redesign.recents.usecases.RecentsManager
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.redesign.vpn.ChangeServerManager
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ui.ChangeServerViewState
import com.protonvpn.android.redesign.vpn.ui.ChangeServerViewStateFlow
import com.protonvpn.android.redesign.vpn.ui.VpnStatusViewState
import com.protonvpn.android.redesign.vpn.ui.VpnStatusViewStateFlow
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.telemetry.UpgradeSource
import com.protonvpn.android.telemetry.UpgradeTelemetry
import com.protonvpn.android.tv.main.CountryHighlight
import com.protonvpn.android.ui.home.ServerListUpdaterPrefs
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.DisconnectTrigger
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnErrorUIManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.android.vpn.VpnUiDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import me.proton.core.presentation.savedstate.state
import javax.inject.Inject

private const val DialogStateKey = "dialog"

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    recentsListViewStateFlow: RecentsListViewStateFlow,
    vpnStatusViewStateFlow: VpnStatusViewStateFlow,
    private val recentsManager: RecentsManager,
    private val vpnConnectionManager: VpnConnectionManager,
    private val quickConnectIntent: GetQuickConnectIntent,
    changeServerViewStateFlow: ChangeServerViewStateFlow,
    private val changeServerManager: ChangeServerManager,
    private val upgradeTelemetry: UpgradeTelemetry,
    vpnStatusProviderUI: VpnStatusProviderUI,
    serverListUpdaterPrefs: ServerListUpdaterPrefs,
    private val userSettingsManager: CurrentUserLocalSettingsManager,
    private val vpnErrorUIManager: VpnErrorUIManager,
    @ElapsedRealtimeClock val elapsedRealtimeClock: () -> Long,
) : ViewModel() {

    private val connectionMapHighlightsFlow = vpnStatusProviderUI.uiStatus.map {
        val highlight = it.state.toMapHighlightState()
        val exit = it.server?.exitCountry
        if (highlight != null && exit != null)
            exit to highlight
        else
            null
    }.distinctUntilChanged()

    val mapHighlightState = combine(
        connectionMapHighlightsFlow,
        serverListUpdaterPrefs.lastKnownCountryFlow.distinctUntilChanged()
    ) { connectionHighlight, realCountry ->
        connectionHighlight ?: (realCountry to CountryHighlight.SELECTED)
    }

    val recentsViewState = recentsListViewStateFlow
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

    val vpnStateViewFlow: SharedFlow<VpnStatusViewState> = vpnStatusViewStateFlow
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

    val changeServerViewState: SharedFlow<ChangeServerViewState?> = changeServerViewStateFlow
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

    enum class DialogState {
        CountryInMaintenance, CityInMaintenance, ServerInMaintenance, GatewayInMaintenance, ServerNotAvailable
    }

    private var dialogState by savedStateHandle.state<DialogState?>(null, DialogStateKey)
    val dialogStateFlow = savedStateHandle.getStateFlow<DialogState?>(DialogStateKey, null)

    val eventNavigateToUpgrade = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val snackbarErrorFlow = vpnErrorUIManager.snackErrorFlow

    suspend fun consumeErrorMessage() = vpnErrorUIManager.consumeErrorMessage()

    fun setNetShieldProtocol(netShieldProtocol: NetShieldProtocol) =
        viewModelScope.launch {
            userSettingsManager.updateNetShield(netShieldProtocol)
        }

    fun connect(vpnUiDelegate: VpnUiDelegate, connectIntent: AnyConnectIntent, trigger: ConnectTrigger) {
        vpnConnectionManager.connect(vpnUiDelegate, connectIntent, trigger)
    }
    suspend fun connect(vpnUiDelegate: VpnUiDelegate, trigger: ConnectTrigger) {
        vpnConnectionManager.connect(vpnUiDelegate, quickConnectIntent(), trigger)
    }

    fun changeServer(vpnUiDelegate: VpnUiDelegate) = changeServerManager.changeServer(vpnUiDelegate)

    fun onChangeServerUpgradeButtonShown() {
        upgradeTelemetry.onUpgradeFlowStarted(UpgradeSource.CHANGE_SERVER)
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

    fun disconnect(trigger: DisconnectTrigger) = vpnConnectionManager.disconnect(trigger)

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

private fun VpnState.toMapHighlightState() = when {
    this == VpnState.Connected -> CountryHighlight.CONNECTED
    isEstablishingConnection -> CountryHighlight.CONNECTING
    else -> null
}
