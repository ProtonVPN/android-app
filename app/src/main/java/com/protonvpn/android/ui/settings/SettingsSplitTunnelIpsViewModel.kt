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
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.Setting
import com.protonvpn.android.logging.logUiSettingChange
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.ui.SaveableSettingsViewModel
import com.protonvpn.android.utils.isIPv6
import com.protonvpn.android.utils.isValidIp
import com.protonvpn.android.vpn.usecases.IsIPv6FeatureFlagEnabled
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsSplitTunnelIpsViewModel @Inject constructor(
    private val mainScope: CoroutineScope,
    private val userSettingsManager: CurrentUserLocalSettingsManager,
    private val isIPv6FeatureFlagEnabled: IsIPv6FeatureFlagEnabled,
    savedStateHandle: SavedStateHandle,
) : SaveableSettingsViewModel() {

    enum class Event {
        ShowIPv6EnableSettingDialog,
        ShowIPv6EnabledToast,
    }

    data class State(
        val ips: List<LabeledItem>,
        val showHelp: Boolean,
    )

    private val mode: SplitTunnelingMode = requireNotNull(
        savedStateHandle[SettingsSplitTunnelIpsActivity.SPLIT_TUNNELING_MODE_KEY]
    )
    private val ipAddresses = MutableStateFlow<List<String>>(emptyList())

    val events = MutableSharedFlow<Event>(extraBufferCapacity = 1)

    val state = ipAddresses.map { ips ->
        State(ips.map { LabeledItem(it, it) }, isIPv6FeatureFlagEnabled())
    }

    init {
        viewModelScope.launch {
            ipAddresses.value = valueInSettings()
        }
    }

    suspend fun isValidIp(ip: String) = ip.isValidIp(isIPv6FeatureFlagEnabled())

    fun addAddress(newAddress: String): Boolean {
        val alreadyAdded = ipAddresses.value.contains(newAddress)
        if (!alreadyAdded)
            ipAddresses.value = ipAddresses.value + newAddress
        viewModelScope.launch {
            if (shouldDisplayIPv6SettingDialog(mode, newAddress))
                events.tryEmit(Event.ShowIPv6EnableSettingDialog)
        }
        return !alreadyAdded
    }

    fun removeAddress(item: LabeledItem) {
        ipAddresses.value = ipAddresses.value - item.id
    }

    override fun saveChanges() {
        ProtonLogger.logUiSettingChange(Setting.SPLIT_TUNNEL_IPS, "settings")
        viewModelScope.launch {
            userSettingsManager.updateExcludedIps(ipAddresses.value, mode)
        }
    }

    override suspend fun hasUnsavedChanges(): Boolean = valueInSettings() != ipAddresses.value

    private suspend fun valueInSettings() =
        with(userSettingsManager.rawCurrentUserSettingsFlow.first().splitTunneling) {
            when (mode) {
                SplitTunnelingMode.INCLUDE_ONLY -> includedIps
                SplitTunnelingMode.EXCLUDE_ONLY -> excludedIps
            }
        }

    private suspend fun shouldDisplayIPv6SettingDialog(mode: SplitTunnelingMode, ipText: String): Boolean =
        mode == SplitTunnelingMode.INCLUDE_ONLY && ipText.isIPv6()
            && isIPv6FeatureFlagEnabled() && !userSettingsManager.rawCurrentUserSettingsFlow.first().ipV6Enabled

    fun onEnableIPv6() {
        mainScope.launch {
            userSettingsManager.update { current -> current.copy(ipV6Enabled = true) }
            events.tryEmit(Event.ShowIPv6EnabledToast)
        }
    }
}
