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

package com.protonvpn.android.redesign.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.redesign.settings.ui.NatType
import com.protonvpn.android.redesign.settings.ui.SettingsReconnectHandler
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.userstorage.DontShowAgainStore
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.android.vpn.VpnUiDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsChangeViewModel @Inject constructor(
    private val userSettingsManager: CurrentUserLocalSettingsManager,
    private val reconnectHandler: SettingsReconnectHandler,
) : ViewModel() {

    val showReconnectDialogFlow = reconnectHandler.showReconnectDialogFlow

    fun disableCustomDns(uiDelegate: VpnUiDelegate) {
        viewModelScope.launch {
            userSettingsManager.disableCustomDNS()
            reconnectionCheck(uiDelegate, DontShowAgainStore.Type.DnsChangeWhenConnected)
        }
    }

    fun toggleNetShield() {
        viewModelScope.launch {
            userSettingsManager.toggleNetShield()
        }
    }

    fun updateProtocol(uiDelegate: VpnUiDelegate, newProtocol: ProtocolSelection) {
        viewModelScope.launch {
            userSettingsManager.update { current ->
                if (current.protocol != newProtocol) viewModelScope.launch {
                    reconnectionCheck(uiDelegate, DontShowAgainStore.Type.ProtocolChangeWhenConnected)
                }
                current.copy(protocol = newProtocol)
            }
        }
    }

    fun setNatType(type: NatType) {
        viewModelScope.launch {
            userSettingsManager.setRandomizedNat(type == NatType.Strict)
        }
    }

    fun toggleVpnAccelerator() {
        viewModelScope.launch {
            userSettingsManager.toggleVpnAccelerator()
        }
    }

    fun toggleAltRouting() {
        viewModelScope.launch {
            userSettingsManager.toggleAltRouting()
        }
    }

    fun toggleIPv6(uiDelegate: VpnUiDelegate) {
        viewModelScope.launch {
            userSettingsManager.toggleIPv6()
            reconnectionCheck(uiDelegate, DontShowAgainStore.Type.IPv6ChangeWhenConnected)
        }
    }

    fun toggleLanConnections(uiDelegate: VpnUiDelegate) {
        viewModelScope.launch {
            userSettingsManager.toggleLanConnections()
            reconnectionCheck(uiDelegate, DontShowAgainStore.Type.LanConnectionsChangeWhenConnected)
        }
    }

    fun toggleLanAllowDirectConnections(delegate: VpnUiDelegate) {
        viewModelScope.launch {
            userSettingsManager.toggleLanAllowDirectConnections()
            reconnectionCheck(delegate, DontShowAgainStore.Type.LanConnectionsChangeWhenConnected)
        }
    }

    fun toggleSplitTunneling(uiDelegate: VpnUiDelegate) {
        viewModelScope.launch {
            userSettingsManager.update { current ->
                val oldValue = current.splitTunneling
                val newValue = oldValue.copy(isEnabled = !oldValue.isEnabled)
                if (!oldValue.isEffectivelySameAs(newValue)) viewModelScope.launch {
                    reconnectionCheck(uiDelegate, DontShowAgainStore.Type.SplitTunnelingChangeWhenConnected)
                }
                current.copy(splitTunneling = newValue)
            }
        }
    }

    fun setSplitTunnelingMode(uiDelegate: VpnUiDelegate, newMode: SplitTunnelingMode) {
        viewModelScope.launch {
            userSettingsManager.update { current ->
                val oldValue = current.splitTunneling
                val newValue = oldValue.copy(mode = newMode)
                if (!oldValue.isEffectivelySameAs(newValue)) viewModelScope.launch {
                    reconnectionCheck(uiDelegate, DontShowAgainStore.Type.SplitTunnelingChangeWhenConnected)
                }
                current.copy(splitTunneling = newValue)
            }
        }
    }

    fun onSplitTunnelingUpdated(uiDelegate: VpnUiDelegate) {
        viewModelScope.launch {
            reconnectionCheck(uiDelegate, DontShowAgainStore.Type.SplitTunnelingChangeWhenConnected)
        }
    }

    fun onReconnectClicked(uiDelegate: VpnUiDelegate, dontShowAgain: Boolean, type: DontShowAgainStore.Type) =
        reconnectHandler.onReconnectClicked(uiDelegate, dontShowAgain, type)

    fun dismissReconnectDialog(dontShowAgain: Boolean, type: DontShowAgainStore.Type) =
        reconnectHandler.dismissReconnectDialog(dontShowAgain, type)

    fun showDnsReconnectionDialog(uiDelegate: VpnUiDelegate) {
        viewModelScope.launch {
            reconnectionCheck(uiDelegate, DontShowAgainStore.Type.DnsChangeWhenConnected)
        }
    }

    private suspend fun reconnectionCheck(uiDelegate: VpnUiDelegate, type: DontShowAgainStore.Type) =
        reconnectHandler.reconnectionCheck(uiDelegate, type)
}
