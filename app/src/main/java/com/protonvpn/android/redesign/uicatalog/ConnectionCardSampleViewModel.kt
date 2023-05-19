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
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentViewState
import com.protonvpn.android.redesign.vpn.ui.GetVpnConnectionCardViewState
import com.protonvpn.android.redesign.vpn.ui.VpnStatusViewStateFlow
import com.protonvpn.android.redesign.vpn.ui.VpnConnectionCardViewState
import com.protonvpn.android.redesign.vpn.ui.VpnConnectionState
import com.protonvpn.android.redesign.vpn.ui.VpnStatusViewState
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.DisconnectTrigger
import com.protonvpn.android.vpn.VpnConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ConnectionCardSampleViewModel @Inject constructor(
    getVpnConnectionCardViewState: GetVpnConnectionCardViewState,
    vpnStatusViewStateFlow: VpnStatusViewStateFlow,
    private val vpnConnectionManager: VpnConnectionManager,
    private val serverManager: ServerManager
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
    val cardViewState = getVpnConnectionCardViewState.cardViewState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), initialCardViewState)

    val vpnStateViewFlow: StateFlow<VpnStatusViewState> = vpnStatusViewStateFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = VpnStatusViewState.Disabled()
    )

    fun connect() {
        vpnConnectionManager.connectInBackground(
            serverManager.defaultConnection,
            ConnectTrigger.QuickConnect("UI catalog")
        )
    }

    fun disconnect() {
        vpnConnectionManager.disconnect(DisconnectTrigger.QuickConnect("UI catalog"))
    }
}
