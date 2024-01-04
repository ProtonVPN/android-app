/*
 * Copyright (c) 2023 Proton Technologies AG
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
package com.protonvpn.android.ui.home.vpn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.redesign.vpn.ChangeServerManager
import com.protonvpn.android.redesign.vpn.ui.ChangeServerViewState
import com.protonvpn.android.redesign.vpn.ui.ChangeServerViewStateFlow
import com.protonvpn.android.telemetry.UpgradeSource
import com.protonvpn.android.telemetry.UpgradeTelemetry
import com.protonvpn.android.vpn.VpnUiDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ChangeServerViewModel @Inject constructor(
    private val upgradeTelemetry: UpgradeTelemetry,
    private val changeServerManager: ChangeServerManager,
    changeServerViewStateFlow: ChangeServerViewStateFlow
) : ViewModel() {

    val state: StateFlow<ChangeServerViewState?> = changeServerViewStateFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun changeServer(vpnUiDelegate: VpnUiDelegate) = changeServerManager.changeServer(vpnUiDelegate)

    fun onUpgradeModalOpened() {
        upgradeTelemetry.onUpgradeFlowStarted(UpgradeSource.CHANGE_SERVER)
    }
}
