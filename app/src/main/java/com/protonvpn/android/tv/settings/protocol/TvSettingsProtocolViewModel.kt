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

package com.protonvpn.android.tv.settings.protocol

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.redesign.settings.ui.SettingsReconnectHandler
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.userstorage.DontShowAgainStore
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.android.vpn.VpnUiDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TvSettingsProtocolViewModel @Inject constructor(
    private val userSettingsManager: CurrentUserLocalSettingsManager,
    private val reconnectHandler: SettingsReconnectHandler
) : ViewModel() {

    data class ViewState(
        val selectedProtocol: ProtocolSelection,
        val reconnectDialog: DontShowAgainStore.Type?
    )

    private val protocolSetting = userSettingsManager
        .rawCurrentUserSettingsFlow
        .map { it.protocol }
        .distinctUntilChanged()

    val eventNavigateBack = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val viewState = combine(
        protocolSetting,
        reconnectHandler.showReconnectDialogFlow
    ) { protocol, dialog ->
        ViewState(selectedProtocol = protocol, reconnectDialog = dialog)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun onProtocolSelected(uiVpnUiDelegate: VpnUiDelegate, newProtocol: ProtocolSelection) {
        viewModelScope.launch {
            val oldValue = protocolSetting.first()
            if (oldValue != newProtocol) {
                userSettingsManager.updateProtocol(newProtocol)
                reconnectHandler.reconnectionCheck(uiVpnUiDelegate, DontShowAgainStore.Type.ProtocolChangeWhenConnected)
                if (reconnectHandler.showReconnectDialogFlow.value == null) {
                    eventNavigateBack.tryEmit(Unit)
                }
            }
        }
    }

    fun onReconnectClicked(uiDelegate: VpnUiDelegate, dontShowAgain: Boolean) {
        reconnectHandler.onReconnectClicked(
            uiDelegate,
            dontShowAgain,
            DontShowAgainStore.Type.ProtocolChangeWhenConnected
        )
        eventNavigateBack.tryEmit(Unit)
    }

    fun dismissReconnectDialog(dontShowAgain: Boolean) {
        reconnectHandler.dismissReconnectDialog(dontShowAgain, DontShowAgainStore.Type.ProtocolChangeWhenConnected)
        eventNavigateBack.tryEmit(Unit)
    }
}
