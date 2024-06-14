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
import com.protonvpn.android.redesign.settings.ui.SettingsReconnectHandler
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.settings.data.SplitTunnelingSettings
import com.protonvpn.android.ui.settings.currentModeApps
import com.protonvpn.android.userstorage.DontShowAgainStore
import com.protonvpn.android.vpn.VpnUiDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.proton.core.presentation.savedstate.state
import javax.inject.Inject

private const val InitialSettingsStateKey = "initial settings"

@HiltViewModel
class TvSettingsSplitTunnelingMainVM @Inject constructor(
    private val mainScope: CoroutineScope,
    private val installedAppsProvider: InstalledAppsProvider,
    private val userSettingsManager: CurrentUserLocalSettingsManager,
    private val reconnectHandler: SettingsReconnectHandler,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    data class MainViewState(
        val isEnabled: Boolean,
        val mode: SplitTunnelingMode,
        val currentModeApps: List<CharSequence>?,
    )

    private var initialValue: SplitTunnelingSettings? by savedStateHandle.state(null, InitialSettingsStateKey)

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

    val showReconnectDialogFlow = reconnectHandler.showReconnectDialogFlow
    val eventNavigateBack = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var navigateBackAfterDialog = false

    init {
        viewModelScope.launch {
            initialValue = userSettingsManager.rawCurrentUserSettingsFlow.first().splitTunneling
        }
    }

    fun onToggleEnabled(uiDelegate: VpnUiDelegate) {
        mainScope.launch {
            val newSplitTunneling = userSettingsManager.toggleSplitTunneling()?.splitTunneling
            if (newSplitTunneling != null && initialValue?.isEffectivelySameAs(newSplitTunneling) == false) {
                reconnectionCheck(uiDelegate)
                initialValue = newSplitTunneling
            }
        }
    }

    fun onModeChanged(mode: SplitTunnelingMode) {
        mainScope.launch {
            userSettingsManager.updateSplitTunnelingMode(mode)
        }
    }

    fun onNavigateBack(uiDelegate: VpnUiDelegate) {
        viewModelScope.launch {
            val currentSettings = userSettingsManager.rawCurrentUserSettingsFlow.first().splitTunneling
            if (initialValue != null && initialValue?.isEffectivelySameAs(currentSettings) == false) {
                reconnectionCheck(uiDelegate)
            } else {
                eventNavigateBack.tryEmit(Unit)
            }
        }
    }

    fun onReconnectClicked(uiDelegate: VpnUiDelegate, dontShowAgain: Boolean) {
        reconnectHandler.onReconnectClicked(
            uiDelegate,
            dontShowAgain,
            DontShowAgainStore.Type.SplitTunnelingChangeWhenConnected
        )
        if (navigateBackAfterDialog) {
            eventNavigateBack.tryEmit(Unit)
        }
    }

    fun dismissReconnectDialog(dontShowAgain: Boolean) {
        reconnectHandler.dismissReconnectDialog(
            dontShowAgain,
            DontShowAgainStore.Type.SplitTunnelingChangeWhenConnected
        )
        if (navigateBackAfterDialog) {
            eventNavigateBack.tryEmit(Unit)
        }
    }

    private suspend fun reconnectionCheck(uiDelegate: VpnUiDelegate) =
        reconnectHandler.reconnectionCheck(uiDelegate, DontShowAgainStore.Type.SplitTunnelingChangeWhenConnected)

    private fun mainState(splitTunneling: SplitTunnelingSettings, appNames: List<CharSequence>?) =
        MainViewState(
            isEnabled = splitTunneling.isEnabled,
            mode = splitTunneling.mode,
            currentModeApps = appNames
        )
}
