/*
 * Copyright (c) 2026. Proton AG
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

package com.protonvpn.android.tv.settings.main.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.auth.usecase.Logout
import com.protonvpn.android.vpn.VpnStatusProviderUI
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TvSettingsViewModel @Inject constructor(
    viewStateFlow: TvSettingsViewStateFlow,
    private val mainScope: CoroutineScope,
    private val vpnStatusProviderUI: VpnStatusProviderUI,
    private val logout: Logout,
) : ViewModel() {

    val viewState: StateFlow<TvSettingsViewState?> =
        viewStateFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun isConnected(): Boolean = vpnStatusProviderUI.isConnected

    fun signOut() {
        // Logging out clears the UI, run it in the main scope so that it's not cancelled.
        mainScope.launch { logout() }
    }
}
