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

package com.protonvpn.android.ui.home.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.vpn.VpnStatusProviderUI
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.withIndex
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    userSettings: EffectiveCurrentUserSettings,
    vpnStatusProviderUI: VpnStatusProviderUI,
    serverManager: ServerManager,
    userPlanManager: UserPlanManager
) : ViewModel() {
    val updateMapEvent = combine(
        userSettings.secureCore,
        vpnStatusProviderUI.isConnectedOrDisconnectedFlow,
        serverManager.serverListVersion,
        merge(flowOf(Unit), userPlanManager.planChangeFlow) // Need to have initial value in order not to block combine
    ) { _, _, _, _ ->
    }.withIndex() // Value need to change each time
    .asLiveData()
}
