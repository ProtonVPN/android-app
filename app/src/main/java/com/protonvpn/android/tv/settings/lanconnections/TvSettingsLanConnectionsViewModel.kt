/*
 * Copyright (c) 2025. Proton AG
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

package com.protonvpn.android.tv.settings.lanconnections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.redesign.settings.ui.SettingsReconnectHandler
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.userstorage.DontShowAgainStore
import com.protonvpn.android.vpn.VpnUiDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private val RECONNECT_TYPE = DontShowAgainStore.Type.LanConnectionsChangeWhenConnected

@HiltViewModel
class TvSettingsLanConnectionsViewModel @Inject constructor(
    private val userSettingsManager: CurrentUserLocalSettingsManager,
    private val reconnectDialogHandler: SettingsReconnectHandler,
) : ViewModel() {

    data class ViewState(
        val isEnabled: Boolean,
        val showReconnectDialog: Boolean
    )

    val viewState = combine(
        userSettingsManager.rawCurrentUserSettingsFlow, reconnectDialogHandler.showReconnectDialogFlow
    ) { settings, showReconnectDialog ->
        ViewState(
            settings.lanConnections,
            showReconnectDialog == RECONNECT_TYPE
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun toggleLanConnections(uiVpnUiDelegate: VpnUiDelegate) {
        viewModelScope.launch {
            userSettingsManager.toggleLanConnections()
            reconnectDialogHandler.reconnectionCheck(uiVpnUiDelegate, RECONNECT_TYPE)
        }
    }

    fun onReconnectClicked(uiDelegate: VpnUiDelegate) {
        reconnectDialogHandler.onReconnectClicked(uiDelegate, false, RECONNECT_TYPE)
    }

    fun onReconnectDismissed() {
        reconnectDialogHandler.dismissReconnectDialog(false, RECONNECT_TYPE)
    }
}
