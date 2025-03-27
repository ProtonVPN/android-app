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

import com.protonvpn.android.redesign.settings.ui.SettingsViewModel.SettingViewState
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

data class CustomDnsViewState(
    val dnsViewState: SettingViewState.CustomDns,
    val isConnected: Boolean,
    val addDnsState: AddDnsState = AddDnsResult.Finished
) {
    val showAddDnsButton get() =
        !dnsViewState.isPrivateDnsActive && (dnsViewState.customDns.isEmpty() || dnsViewState.value)
}

abstract class DnsSettingsDataSource {
    private val addDnsStateFlow = MutableStateFlow<AddDnsState>(AddDnsResult.Finished)

    data class UndoSnackbarData(
        val removedItem: String,
        val position: Int
    )

    val undoSnackbarFlow = MutableSharedFlow<UndoSnackbarData>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val eventShowNetShieldConflict = Channel<Unit>(capacity = 1, BufferOverflow.DROP_OLDEST)

    protected abstract val dnsViewState: Flow<SettingViewState.CustomDns?>
    protected abstract val isConnected: Flow<Boolean>

    val viewStateFlow: Flow<CustomDnsViewState?> by lazy {
        combine(
            dnsViewState,
            addDnsStateFlow,
            isConnected
        ) { dnsViewState, addDnsState, isConnected ->
            dnsViewState?.let {
                CustomDnsViewState(
                    dnsViewState = it,
                    addDnsState = addDnsState,
                    isConnected = isConnected
                )
            }
        }
    }

    abstract fun toggleEnabled()
    abstract fun updateDnsList(newList: List<String>)
    protected abstract fun performAddDnsAddress(address: String)
    protected abstract fun performRemoveDnsAddress(address: String, position: Int)
    protected abstract fun performUndoRemoval(undoData: UndoSnackbarData)
    abstract fun shouldShowNetShieldConflict(): Boolean

    fun addDnsAddress(address: String) {
        performAddDnsAddress(address)
        setAddDnsState(AddDnsResult.Finished)
        
        if (shouldShowNetShieldConflict()) {
            eventShowNetShieldConflict.trySend(Unit)
        }
    }

    fun removeDnsAddress(currentList: List<String>, address: String) {
        val position = currentList.indexOf(address)
        performRemoveDnsAddress(address, position)

        undoSnackbarFlow.tryEmit(UndoSnackbarData(
            removedItem = address,
            position = position
        ))
    }

    fun undoRemoval(undoData: UndoSnackbarData) {
        performUndoRemoval(undoData)
    }

    fun setAddDnsState(state: AddDnsState) {
        addDnsStateFlow.value = state
    }
    
    fun resetAddDnsError() {
        if (addDnsStateFlow.value is AddDnsError) {
            addDnsStateFlow.value = AddDnsResult.WaitingForInput
        }
    }
} 