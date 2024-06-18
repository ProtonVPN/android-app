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

package com.protonvpn.android.tv.settings.splittunneling

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.components.InstalledAppsProvider
import com.protonvpn.android.redesign.base.ui.nav.Screen
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.ui.settings.LabeledItem
import com.protonvpn.android.ui.settings.SplitTunnelingAppsViewModelHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.proton.core.util.kotlin.DispatcherProvider
import javax.inject.Inject

@HiltViewModel
class TvSettingsSplitTunnelingAppsVM @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mainScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    installedAppsProvider: InstalledAppsProvider,
    private val userSettingsManager: CurrentUserLocalSettingsManager
) : ViewModel() {

    private val mode = AppsScreen.decodeArg<SplitTunnelingMode>(requireNotNull(savedStateHandle[Screen.ARG_NAME]))

    private val helper = SplitTunnelingAppsViewModelHelper(
        viewModelScope,
        dispatcherProvider,
        installedAppsProvider,
        appsFromSettings(mode),
        forTv = true,
    )

    val viewState = helper.viewState

    fun toggleLoadSystemApps() {
        helper.toggleLoadSystemApps()
    }

    fun addApp(item: LabeledItem) {
        updateApps(mode) { apps -> apps + item.id }
    }
    fun removeApp(item: LabeledItem) {
        updateApps(mode) { apps -> apps - item.id }
    }

    private fun updateApps(mode: SplitTunnelingMode, transform: (List<String>) -> List<String>) {
        mainScope.launch {
            userSettingsManager.updateSplitTunnelSettings { old ->
                when (mode) {
                    SplitTunnelingMode.EXCLUDE_ONLY -> old.copy(excludedApps = transform(old.excludedApps))
                    SplitTunnelingMode.INCLUDE_ONLY -> old.copy(includedApps = transform(old.includedApps))
                }
            }
        }
    }

    private fun appsFromSettings(mode: SplitTunnelingMode) =
        userSettingsManager.rawCurrentUserSettingsFlow
            .map {
                when (mode) {
                    SplitTunnelingMode.EXCLUDE_ONLY -> it.splitTunneling.excludedApps
                    SplitTunnelingMode.INCLUDE_ONLY -> it.splitTunneling.includedApps
                }.toSet()
            }
            .distinctUntilChanged()
}
