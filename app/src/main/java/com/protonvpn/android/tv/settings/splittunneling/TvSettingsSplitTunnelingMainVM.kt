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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.components.InstalledAppsProvider
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.settings.data.SplitTunnelingSettings
import com.protonvpn.android.ui.settings.currentModeApps
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TvSettingsSplitTunnelingMainVM @Inject constructor(
    private val mainScope: CoroutineScope,
    private val installedAppsProvider: InstalledAppsProvider,
    private val userSettingsManager: CurrentUserLocalSettingsManager,
) : ViewModel() {

    data class MainViewState(
        val isEnabled: Boolean,
        val mode: SplitTunnelingMode,
        val currentModeApps: List<CharSequence>?,
    )

    // TODO: saveable
    private var initialSplitTunnelingValue: SplitTunnelingSettings? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    val mainViewState: StateFlow<MainViewState?> = userSettingsManager
        .rawCurrentUserSettingsFlow
        .map { it.splitTunneling }
        .flatMapLatest { splitTunneling ->
            flow {
                val appPackages = splitTunneling.currentModeApps()
                emit(mainState(splitTunneling, appPackages.takeIf { it.isEmpty() }))
                if (appPackages.isNotEmpty()) {
                    val appNames = installedAppsProvider.getNamesOfInstalledApps(appPackages)
                    emit(mainState(splitTunneling, appNames))
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    init {
        viewModelScope.launch {
            initialSplitTunnelingValue = userSettingsManager.rawCurrentUserSettingsFlow.first().splitTunneling
        }
    }

    fun onToggleEnabled() {
        mainScope.launch {
            userSettingsManager.toggleSplitTunneling()
        }
    }

    fun onModeChanged(mode: SplitTunnelingMode) {
        mainScope.launch {
            userSettingsManager.updateSplitTunnelingMode(mode)
        }
    }

    private fun mainState(splitTunneling: SplitTunnelingSettings, appNames: List<CharSequence>?) =
        MainViewState(
            isEnabled = splitTunneling.isEnabled,
            mode = splitTunneling.mode,
            currentModeApps = appNames
        )
}
