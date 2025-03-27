/*
 * Copyright (c) 2024 Proton AG
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

package com.protonvpn.android.redesign.settings.ui.customdns

import com.protonvpn.android.R
import com.protonvpn.android.redesign.settings.ui.SettingsViewModel.SettingViewState
import com.protonvpn.android.utils.isValidIp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

abstract class UndoCustomDnsRemove(
    val removedItem: String,
    val position: Int
) {
    abstract operator fun invoke()
}

sealed class CustomDnsViewState(
    open val dnsViewState: SettingViewState.CustomDns,
) {
    data class DnsListState(
        override val dnsViewState: SettingViewState.CustomDns,
    ) : CustomDnsViewState(dnsViewState) {
        val showAddDnsButton get() =
            !dnsViewState.isPrivateDnsActive && (dnsViewState.customDns.isEmpty() || dnsViewState.value)
    }

    data class AddNewDnsState(
        override val dnsViewState: SettingViewState.CustomDns,
        val addDnsError: AddDnsError? = null
    ) : CustomDnsViewState(dnsViewState)
}

enum class AddDnsError(val errorRes: Int) {
    EmptyInput(R.string.settings_add_dns_empty_input),
    InvalidInput(R.string.settings_add_dns_invalid_input),
    DuplicateInput(R.string.settings_add_dns_duplicate_input)
}

class DnsSettingViewModelHelper(
    viewModelScope: CoroutineScope,
    private val dnsViewState: Flow<SettingViewState.CustomDns?>
) {

    private val openAddDns = MutableStateFlow(false)
    private val addDnsErrorFlow = MutableStateFlow<AddDnsError?>(null)

    enum class Event {
        NetShieldConflictDetected,
        CustomDnsSettingChangedWhenConnected,
    }
    val events = Channel<Event>(Channel.Factory.UNLIMITED)

    val customDnsSettingState: StateFlow<CustomDnsViewState?> =
        combine(
            dnsViewState,
            openAddDns,
            addDnsErrorFlow,
        ) { dnsViewState, isAddingDns, addDnsError ->
            dnsViewState?.let {
                if (isAddingDns) {
                    CustomDnsViewState.AddNewDnsState(
                        dnsViewState = it,
                        addDnsError = addDnsError
                    )
                } else {
                    CustomDnsViewState.DnsListState(
                        dnsViewState = it,
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun validateAndAddDnsAddress(
        newDns: String,
        changeConflictsWithNetShield: Boolean,
        performAddDnsAddress: () -> Unit
    ) {
        val validationError = when {
            newDns.isEmpty() -> AddDnsError.EmptyInput
            customDnsSettingState.value?.dnsViewState?.customDns?.contains(newDns) == true ->
                AddDnsError.DuplicateInput
            !newDns.isValidIp(allowIpv6 = true) -> AddDnsError.InvalidInput
            else -> null
        }

        if (validationError != null) {
            addDnsErrorFlow.value = validationError
        } else {
            performAddDnsAddress()
            closeAddDnsScreen()
            if (changeConflictsWithNetShield) {
                events.trySend(Event.NetShieldConflictDetected)
            }
        }
    }

    fun openAddDnsScreen() {
        openAddDns.value = true
    }

    fun closeAddDnsScreen() {
        openAddDns.value = false
        addDnsErrorFlow.value = null
    }

    fun onAddDnsTextChanged() {
        addDnsErrorFlow.value = null
    }
} 