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

package com.protonvpn.android.redesign.settings.ui

import androidx.lifecycle.SavedStateHandle
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.UiReconnect
import com.protonvpn.android.userstorage.DontShowAgainStore
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.android.vpn.VpnUiDelegate
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.proton.core.presentation.savedstate.state
import javax.inject.Inject

private const val ReconnectDialogStateKey = "reconnect_dialog"

@ViewModelScoped
class SettingsReconnectHandler @Inject constructor(
    private val mainScope: CoroutineScope,
    private val vpnConnectionManager: VpnConnectionManager,
    private val vpnStatusProviderUI: VpnStatusProviderUI,
    private val dontShowAgainStore: DontShowAgainStore,
    savedStateHandle: SavedStateHandle,
) {
    private var showReconnectDialog by savedStateHandle.state<DontShowAgainStore.Type?>(null, ReconnectDialogStateKey)
    val showReconnectDialogFlow = savedStateHandle.getStateFlow<DontShowAgainStore.Type?>(ReconnectDialogStateKey, null)

    fun onReconnectClicked(uiDelegate: VpnUiDelegate, dontShowAgain: Boolean, type: DontShowAgainStore.Type) {
        showReconnectDialog = null
        mainScope.launch {
            if (dontShowAgain)
                dontShowAgainStore.setChoice(type, DontShowAgainStore.Choice.Positive)
            reconnect(uiDelegate)
        }
    }

    fun dismissReconnectDialog(dontShowAgain: Boolean, type: DontShowAgainStore.Type) {
        showReconnectDialog = null
        mainScope.launch {
            if (dontShowAgain)
                dontShowAgainStore.setChoice(type, DontShowAgainStore.Choice.Negative)
        }
    }

    // Returns if dialog is shown or not.
    suspend fun reconnectionCheck(uiDelegate: VpnUiDelegate, type: DontShowAgainStore.Type) {
        if (vpnStatusProviderUI.isEstablishingOrConnected) {
            when (dontShowAgainStore.getChoice(type)) {
                DontShowAgainStore.Choice.Positive -> reconnect(uiDelegate)
                DontShowAgainStore.Choice.Negative -> {} // No action
                DontShowAgainStore.Choice.ShowDialog -> showReconnectDialog = type
            }
        }
    }

    private fun reconnect(uiDelegate: VpnUiDelegate) {
        ProtonLogger.log(UiReconnect, "settings")
        vpnConnectionManager.reconnect("user via settings change", uiDelegate)
    }
}
