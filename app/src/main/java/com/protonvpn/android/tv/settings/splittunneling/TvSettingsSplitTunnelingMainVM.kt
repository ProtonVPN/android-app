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
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.UiReconnect
import com.protonvpn.android.redesign.settings.ui.SettingsReconnectHandler
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.settings.data.SplitTunnelingSettings
import com.protonvpn.android.userstorage.DontShowAgainStore
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.android.vpn.VpnUiDelegate
import com.protonvpn.android.vpn.isConnectedOrConnecting
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
    private val reconnectDialogHandler: SettingsReconnectHandler,
    private val vpnConnectionManager: VpnConnectionManager,
    vpnStatusProviderUI: VpnStatusProviderUI,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    data class MainViewState(
        val isEnabled: Boolean,
        val needsReconnect: Boolean,
        val mode: SplitTunnelingMode,
        val currentModeApps: List<CharSequence>?,
    )

    private var appliedSettings: SplitTunnelingSettings? by savedStateHandle.state(null, InitialSettingsStateKey)
    private val appliedSettingsFlow: StateFlow<SplitTunnelingSettings?> =
        savedStateHandle.getStateFlow(InitialSettingsStateKey, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val selectedAppNames = userSettingsManager
        .rawCurrentUserSettingsFlow
        .map { it.splitTunneling.currentModeApps() }
        .distinctUntilChanged()
        .flatMapLatest { appPackages ->
            flow {
                emit(emptyList())
                if (appPackages.isNotEmpty()) {
                    emit(installedAppsProvider.getNamesOfInstalledApps(appPackages))
                }
            }
        }

    val mainViewState: StateFlow<MainViewState?> = combine(
        userSettingsManager.rawCurrentUserSettingsFlow.map { it.splitTunneling }.distinctUntilChanged(),
        appliedSettingsFlow,
        selectedAppNames,
        vpnStatusProviderUI.uiStatus
    ) { splitTunneling, appliedSettings, selectedAppNames, vpnStatus ->
        val isConnected = vpnStatus.state.isConnectedOrConnecting()
        val needsReconnect =
            isConnected && appliedSettings != null && !appliedSettings.isEffectivelySameAs(splitTunneling)
        mainState(splitTunneling, needsReconnect, selectedAppNames)
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    val showReconnectDialogFlow = reconnectDialogHandler.showReconnectDialogFlow
    val eventNavigateBack = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        updateInitialSettings()
    }

    fun onToggleEnabled() {
        mainScope.launch {
            userSettingsManager.toggleSplitTunneling()?.splitTunneling
        }
    }

    fun onModeChanged(mode: SplitTunnelingMode) {
        mainScope.launch {
            userSettingsManager.updateSplitTunnelingMode(mode)
        }
    }

    fun onNavigateBack(uiDelegate: VpnUiDelegate) {
        viewModelScope.launch {
            val newSplitTunneling = userSettingsManager.rawCurrentUserSettingsFlow.first().splitTunneling
            if (appliedSettings != null && appliedSettings?.isEffectivelySameAs(newSplitTunneling) == false) {
                reconnectionCheck(uiDelegate)
            }
            val showsDialog = showReconnectDialogFlow.value != null
            if (!showsDialog) {
                eventNavigateBack.tryEmit(Unit)
            }
        }
    }

    fun onBannerReconnect(uiDelegate: VpnUiDelegate) {
        ProtonLogger.log(UiReconnect, "TV settings")
        vpnConnectionManager.reconnect("user via settings change", uiDelegate)
        updateInitialSettings()
    }

    fun onDialogReconnectClicked(uiDelegate: VpnUiDelegate, dontShowAgain: Boolean) {
        reconnectDialogHandler.onReconnectClicked(
            uiDelegate,
            dontShowAgain,
            DontShowAgainStore.Type.SplitTunnelingChangeWhenConnected
        )
        eventNavigateBack.tryEmit(Unit)
    }

    fun dismissReconnectDialog(dontShowAgain: Boolean) {
        reconnectDialogHandler.dismissReconnectDialog(
            dontShowAgain,
            DontShowAgainStore.Type.SplitTunnelingChangeWhenConnected
        )
        eventNavigateBack.tryEmit(Unit)
    }

    private suspend fun reconnectionCheck(uiDelegate: VpnUiDelegate) =
        reconnectDialogHandler.reconnectionCheck(uiDelegate, DontShowAgainStore.Type.SplitTunnelingChangeWhenConnected)

    private fun updateInitialSettings() {
        viewModelScope.launch {
            appliedSettings = userSettingsManager.rawCurrentUserSettingsFlow.first().splitTunneling
        }
    }

    private fun mainState(
        splitTunneling: SplitTunnelingSettings,
        needsReconnect: Boolean,
        appNames: List<CharSequence>?
    ) = MainViewState(
        isEnabled = splitTunneling.isEnabled,
        needsReconnect = needsReconnect,
        mode = splitTunneling.mode,
        currentModeApps = appNames
    )
}
