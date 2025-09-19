/*
 * Copyright (c) 2025 Proton AG
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

package com.protonvpn.android.tv.settings.customdns.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.redesign.settings.ui.customdns.AddDnsError
import com.protonvpn.android.redesign.vpn.usecases.SettingsForConnection
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.utils.isValidIp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TvSettingsAddCustomDnsViewModel @Inject constructor(
    private val mainScope: CoroutineScope,
    private val settingsForConnection: SettingsForConnection,
    private val userSettingsManager: CurrentUserLocalSettingsManager,
) : ViewModel() {

    sealed interface Event {

        data object OnCustomDnsAdded : Event

        data object OnShowNetShieldConflictDialog : Event

    }

    data class ViewState(val error: AddDnsError?)

    private val eventChannel = Channel<Event>(
        capacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val customDnsErrorFlow = MutableStateFlow<AddDnsError?>(value = null)

    val eventChannelReceiver: ReceiveChannel<Event> = eventChannel

    val viewStateFlow: StateFlow<ViewState?> = customDnsErrorFlow
        .map(::ViewState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    fun onCustomDnsChanged() {
        customDnsErrorFlow.value = null
    }

    fun onAddCustomDns(newCustomDns: String) {
        mainScope.launch {
            val customDnsValidationError = getCustomDnsValidationError(newCustomDns)

            if (customDnsValidationError != null) {
                customDnsErrorFlow.value = customDnsValidationError
                return@launch
            }

            val hasNetShieldConflict = hasNetShieldConflict()

            updateCustomDns(newCustomDns)

            if (hasNetShieldConflict) {
                eventChannel.send(Event.OnShowNetShieldConflictDialog)
            } else {
                eventChannel.send(Event.OnCustomDnsAdded)
            }
        }
    }

    private suspend fun hasNetShieldConflict(): Boolean = settingsForConnection
        .getFlowForCurrentConnection()
        .first()
        .connectionSettings
        .let { connectionSettings ->
            val isNetShieldEnabled = connectionSettings.netShield != NetShieldProtocol.DISABLED
            val isCustomDnsEmpty = connectionSettings.customDns.rawDnsList.isEmpty()
            isNetShieldEnabled && isCustomDnsEmpty
        }


    private suspend fun getCustomDnsValidationError(customDns: String): AddDnsError? = when {
        customDns.isEmpty() -> AddDnsError.EmptyInput
        !customDns.isValidIp(allowIpv6 = true) -> AddDnsError.InvalidInput
        userSettingsManager.rawCurrentUserSettingsFlow
            .first()
            .customDns
            .rawDnsList
            .contains(customDns) -> AddDnsError.DuplicateInput

        else -> null
    }

    private suspend fun updateCustomDns(newCustomDns: String) {
        userSettingsManager.updateCustomDns { customDns ->
            val newCustomDnsList = customDns.rawDnsList.plus(newCustomDns)

            customDns.copy(
                toggleEnabled = true,
                rawDnsList = newCustomDnsList,
            )
        }
    }

}
