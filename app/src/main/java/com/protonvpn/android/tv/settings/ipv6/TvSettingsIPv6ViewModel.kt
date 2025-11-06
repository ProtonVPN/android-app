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

package com.protonvpn.android.tv.settings.ipv6

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.redesign.settings.ui.SettingsReconnectHandler
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.userstorage.DontShowAgainStore
import com.protonvpn.android.vpn.VpnUiDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private val RECONNECT_TYPE = DontShowAgainStore.Type.IPv6ChangeWhenConnected

@HiltViewModel
class TvSettingsIPv6ViewModel @Inject constructor(
    private val mainScope: CoroutineScope,
    private val settingsReconnectHandler: SettingsReconnectHandler,
    private val userSettingsManager: CurrentUserLocalSettingsManager,

    ) : ViewModel() {

    private val eventChannel = Channel<Event>(
        capacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    sealed interface Event {
        data object OnClose : Event
    }

    data class ViewState(
        val isIPv6Enabled: Boolean,
        val showReconnectDialog: Boolean,
    )

    val eventChannelReceiver: ReceiveChannel<Event> = eventChannel

    val viewState: StateFlow<ViewState?> = combine(
        userSettingsManager.rawCurrentUserSettingsFlow,
        settingsReconnectHandler.showReconnectDialogFlow
    ) { localUserSettings, showReconnectDialog ->
        ViewState(localUserSettings.ipV6Enabled, showReconnectDialog == RECONNECT_TYPE)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    fun toggle(uiVpnUiDelegate: VpnUiDelegate) {
        mainScope.launch {
            userSettingsManager.toggleIPv6()
            settingsReconnectHandler.reconnectionCheck(uiVpnUiDelegate, RECONNECT_TYPE)
        }
    }

    fun onReconnectNow(vpnUiDelegate: VpnUiDelegate) {
        settingsReconnectHandler.onReconnectClicked(
            uiDelegate = vpnUiDelegate,
            dontShowAgain = false,
            type = DontShowAgainStore.Type.DnsChangeWhenConnected,
        )

        closeScreen()
    }

    fun onDismissReconnectNowDialog() {
        settingsReconnectHandler.dismissReconnectDialog(
            dontShowAgain = false,
            type = RECONNECT_TYPE,
        )
    }

    private fun closeScreen() {
        viewModelScope.launch {
            eventChannel.send(Event.OnClose)
        }
    }
}
