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
import com.protonvpn.android.managed.AutoLoginManager
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.redesign.vpn.ui.VpnStatusViewState
import com.protonvpn.android.redesign.vpn.ui.VpnStatusViewStateFlow
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.ui.storage.UiStateStorage
import com.protonvpn.android.update.ShouldShowAppUpdateDotFlow
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnUiDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    vpnStatusViewStateFlow: VpnStatusViewStateFlow,
    private val vpnConnectionManager: VpnConnectionManager,
    serverManager2: ServerManager2,
    private val autoLoginManager: AutoLoginManager,
    uiStateStorage: UiStateStorage,
    shouldShowAppUpdateDotFlow: ShouldShowAppUpdateDotFlow,
) : ViewModel() {
    private val uiStateFlow = uiStateStorage.state
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    val vpnStateViewFlow: StateFlow<VpnStatusViewState> = vpnStatusViewStateFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), VpnStatusViewState.Loading)

    val showCountriesFlow = serverManager2.hasAnyCountryFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val showGatewaysFlow = serverManager2.hasAnyGatewaysFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val isMinimalStateReadyFlow: StateFlow<Boolean> = combine(
        vpnStateViewFlow,
        showGatewaysFlow,
        showCountriesFlow,
    ) { vpnStateView, showGateways, showCountries ->
        vpnStateView != VpnStatusViewState.Loading && showGateways != null && showCountries != null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    val autoShowInfoSheet = uiStateFlow.map { it.shouldPromoteProfiles }.distinctUntilChanged()

    val showAppUpdateDot = shouldShowAppUpdateDotFlow

    // Must be fast, it's used in SplashScreen.setKeepOnScreenCondition
    val isMinimalStateReady: Boolean get() = isMinimalStateReadyFlow.value

    fun connect(vpnUiDelegate: VpnUiDelegate, connectIntent: AnyConnectIntent, trigger: ConnectTrigger) {
        vpnConnectionManager.connect(vpnUiDelegate, connectIntent, trigger)
    }

    fun retryAutoLogin() {
        autoLoginManager.retry()
    }
}
