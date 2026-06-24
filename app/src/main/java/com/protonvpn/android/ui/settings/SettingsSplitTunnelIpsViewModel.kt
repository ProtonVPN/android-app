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
import com.protonvpn.android.R
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.Setting
import com.protonvpn.android.logging.logUiSettingChange
import com.protonvpn.android.models.vpn.usecase.toIPAddress
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.ui.SaveableSettingsViewModel
import com.protonvpn.android.utils.isIPv6
import com.protonvpn.android.utils.isValidIp
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
    savedStateHandle: SavedStateHandle,
) : SaveableSettingsViewModel() {

    enum class Event {
        ShowIPv6EnableSettingDialog,
        ShowIPv6EnabledToast,
    }

    data class State(
        val ips: List<LabeledItem>,
    )

    private val mode: SplitTunnelingMode = requireNotNull(
        savedStateHandle[SettingsSplitTunnelIpsActivity.SPLIT_TUNNELING_MODE_KEY]
    )
    private val ipAddresses = MutableStateFlow<List<String>>(emptyList())

    val events = MutableSharedFlow<Event>(extraBufferCapacity = 1)

    val state = ipAddresses.map { ips ->
        State(ips.map { LabeledItem(it, it) })
    }

    init {
        viewModelScope.launch {
            ipAddresses.value = valueInSettings()
        }
    }

    fun isValidIpRange(ip: String) = ip.isValidIp(allowPrefix = true)

    // Returns null on success and string ID of error message on error.
    fun addAddressIfValid(newAddressStr: String): Int? =
        if (isValidIpRange(newAddressStr)) {
            val newAddressIP = newAddressStr.toIPAddress()
            val newAddressStr = if (newAddressIP.isIPv4)
                newAddressStr.removeSuffix("/32")
            else
                newAddressStr.removeSuffix("/128")
            val alreadyAdded = ipAddresses.value.any { it.toIPAddress() == newAddressIP }
            if (!alreadyAdded) {
                ipAddresses.value += newAddressStr
                viewModelScope.launch {
                    if (shouldDisplayIPv6SettingDialog(mode, newAddressStr))
                        events.tryEmit(Event.ShowIPv6EnableSettingDialog)
                }
                null // Success, no error.
            } else {
                when (mode) {
                    SplitTunnelingMode.INCLUDE_ONLY ->
                        R.string.settings_split_tunneling_already_included
                    SplitTunnelingMode.EXCLUDE_ONLY ->
                        R.string.settings_split_tunneling_already_excluded
                }
            }
        } else {
            R.string.inputIpAddressErrorInvalid
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
        mode == SplitTunnelingMode.INCLUDE_ONLY && ipText.isIPv6() && !userSettingsManager.rawCurrentUserSettingsFlow.first().ipV6Enabled

    fun onEnableIPv6() {
        mainScope.launch {
            userSettingsManager.update { current -> current.copy(ipV6Enabled = true) }
            events.tryEmit(Event.ShowIPv6EnabledToast)
        }
    }
}
