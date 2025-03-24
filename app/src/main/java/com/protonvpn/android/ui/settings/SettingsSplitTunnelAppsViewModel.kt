/*
 * Copyright (c) 2021. Proton AG
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

package com.protonvpn.android.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.components.InstalledAppsProvider
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.Setting
import com.protonvpn.android.logging.logUiSettingChange
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.ui.SaveableSettingsViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.proton.core.util.kotlin.DispatcherProvider
import javax.inject.Inject

@HiltViewModel
class SettingsSplitTunnelAppsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    installedAppsProvider: InstalledAppsProvider,
    private val userSettingsManager: CurrentUserLocalSettingsManager,
    savedStateHandle: SavedStateHandle,
) : SaveableSettingsViewModel() {

    private val mode: SplitTunnelingMode = requireNotNull(
        savedStateHandle[SettingsSplitTunnelAppsActivity.SPLIT_TUNNELING_MODE_KEY]
    )

    private val selectedPackages = MutableStateFlow<Set<String>>(emptySet())

    private val helper = SplitTunnelingAppsViewModelHelper(
        viewModelScope,
        dispatcherProvider,
        installedAppsProvider,
        selectedPackages,
        forTv = false,
    )

    val viewState = helper.viewState

    init {
        viewModelScope.launch {
            selectedPackages.value = valueInSettings()
        }
    }

    fun toggleLoadSystemApps() {
        helper.toggleLoadSystemApps()
    }

    fun addApp(item: LabeledItem) {
        selectedPackages.value += item.id
    }
    fun removeApp(item: LabeledItem) {
        selectedPackages.value -= item.id
    }

    override fun saveChanges() {
        ProtonLogger.logUiSettingChange(Setting.SPLIT_TUNNEL_APPS, "settings")
        viewModelScope.launch { userSettingsManager.updateSplitTunnelApps(selectedPackages.value.toList(), mode) }
    }

    override suspend fun hasUnsavedChanges() = selectedPackages.value != valueInSettings()

    private suspend fun valueInSettings(): Set<String> =
        with(userSettingsManager.rawCurrentUserSettingsFlow.first().splitTunneling) {
            when (mode) {
                SplitTunnelingMode.INCLUDE_ONLY -> includedApps
                SplitTunnelingMode.EXCLUDE_ONLY -> excludedApps
            }.toHashSet()
        }
}
