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

package com.protonvpn.android.tv.settings.autoconnect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TvSettingsAutoConnectViewModel @Inject constructor(
    private val mainScope: CoroutineScope,
    private val userSettingsManager: CurrentUserLocalSettingsManager
) : ViewModel() {

    data class ViewState(
        val isEnabled: Boolean,
    )

    val viewState = userSettingsManager.rawCurrentUserSettingsFlow
        .map { rawSettings ->
            ViewState(isEnabled = rawSettings.tvAutoConnectOnBoot,)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun toggleAutoConnect() {
        mainScope.launch {
            userSettingsManager.update {
                it.copy(tvAutoConnectOnBoot = !it.tvAutoConnectOnBoot)
            }
        }
    }
}
