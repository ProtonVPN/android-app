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

package com.protonvpn.android.tv.settings.customdns

import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.redesign.settings.ui.SettingsReconnectHandler
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.userstorage.DontShowAgainStore
import com.protonvpn.android.utils.swapOrCurrent
import com.protonvpn.android.vpn.IsPrivateDnsActiveFlow
import com.protonvpn.android.vpn.VpnUiDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import me.proton.core.presentation.savedstate.state
import javax.inject.Inject

@HiltViewModel
class TvSettingsCustomDnsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    isPrivateDnsActiveFlow: IsPrivateDnsActiveFlow,
    private val mainScope: CoroutineScope,
    private val settingsReconnectHandler: SettingsReconnectHandler,
    private val userSettingsManager: CurrentUserLocalSettingsManager,
) : ViewModel() {

    data class SelectedCustomDns(
        val index: Int,
        val customDns: String,
    )

    sealed interface DialogState : Parcelable {

        @Parcelize
        data class Delete(val index: Int, val customDns: String) : DialogState

        @Parcelize
        data object Reconnect : DialogState

    }

    sealed interface Event {

        data object OnClose : Event

    }

    sealed interface ViewState {

        val areCustomDnsSettingsChanged: Boolean

        data class CustomDns(
            override val areCustomDnsSettingsChanged: Boolean,
            val isCustomDnsEnabled: Boolean,
            val customDnsList: List<String>,
            val selectedCustomDns: SelectedCustomDns?,
        ) : ViewState {

            val customDnsCount: Int = customDnsList.size

            val hasSingleCustomDns: Boolean = customDnsCount == 1

            val showOptionsDrawer: Boolean = selectedCustomDns != null

            val canMoveUp: Boolean = selectedCustomDns?.index != 0

            val canMoveDown: Boolean = selectedCustomDns?.index != customDnsCount - 1

        }

        data class Empty(override val areCustomDnsSettingsChanged: Boolean) : ViewState

        data class PrivateDnsConflict(override val areCustomDnsSettingsChanged: Boolean) : ViewState

    }

    private var dialogState by savedStateHandle.state<DialogState?>(
        savedStateHandleKey = DIALOG_STATE_KEY,
        initialValue = null,
    )

    val dialogStateFlow = combine(
        settingsReconnectHandler.showReconnectDialogFlow,
        savedStateHandle.getStateFlow<DialogState?>(key = DIALOG_STATE_KEY, initialValue = null),
    ) { dialogType, dialogState ->
        if (dialogType == DontShowAgainStore.Type.DnsChangeWhenConnected) {
            DialogState.Reconnect
        } else {
            dialogState
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    private val eventChannel = Channel<Event>(
        capacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val eventChannelReceiver: ReceiveChannel<Event> = eventChannel

    private val areCustomDnsSettingsChangedFlow = flow {
        emit(false)

        val initialSettings = userSettingsManager.rawCurrentUserSettingsFlow.first()

        userSettingsManager.rawCurrentUserSettingsFlow.collect { currentSettings ->
            emit(initialSettings.customDns != currentSettings.customDns)
        }
    }

    private val selectedCustomDnsFlow = MutableStateFlow<SelectedCustomDns?>(value = null)

    val viewStateFlow: StateFlow<ViewState?> = combine(
        isPrivateDnsActiveFlow,
        areCustomDnsSettingsChangedFlow,
        userSettingsManager.rawCurrentUserSettingsFlow,
        selectedCustomDnsFlow,
    ) { isPrivateDnsActive, areCustomDnsSettingsChanged, localUserSettings, selectedCustomDns ->
        when {
            isPrivateDnsActive -> ViewState.PrivateDnsConflict(
                areCustomDnsSettingsChanged = areCustomDnsSettingsChanged,
            )

            localUserSettings.customDns.rawDnsList.isEmpty() -> ViewState.Empty(
                areCustomDnsSettingsChanged = areCustomDnsSettingsChanged,
            )

            else -> ViewState.CustomDns(
                areCustomDnsSettingsChanged = areCustomDnsSettingsChanged,
                isCustomDnsEnabled = localUserSettings.customDns.toggleEnabled,
                customDnsList = localUserSettings.customDns.rawDnsList,
                selectedCustomDns = selectedCustomDns,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = null,
    )

    fun onToggleIsCustomDnsEnabled() {
        mainScope.launch {
            userSettingsManager.toggleCustomDNS()
        }
    }

    fun onCustomDnsSelected(index: Int, customDns: String) {
        selectedCustomDnsFlow.value = SelectedCustomDns(
            index = index,
            customDns = customDns,
        )
    }

    fun onMoveCustomDnsToTop(selectedCustomDns: SelectedCustomDns) {
        mainScope.launch {
            val newCustomDnsIndex = 0

            userSettingsManager.updateCustomDns { customDns ->
                customDns.copy(
                    rawDnsList = customDns.rawDnsList
                        .toMutableList()
                        .apply {
                            if (selectedCustomDns.index in indices) {
                                removeAt(index = selectedCustomDns.index)
                                add(index = newCustomDnsIndex, element = selectedCustomDns.customDns)
                            }
                        }
                        .toList()
                )
            }

            onCloseSelectedCustomDnsOptions()
        }
    }

    fun onMoveCustomDnsUp(selectedCustomDns: SelectedCustomDns) {
        mainScope.launch {
            val newCustomDnsIndex = selectedCustomDns.index.minus(1)

            userSettingsManager.updateCustomDns { customDns ->
                customDns.copy(
                    rawDnsList = customDns.rawDnsList.swapOrCurrent(
                        index1 = selectedCustomDns.index,
                        index2 = newCustomDnsIndex,
                    )
                )
            }

            onCloseSelectedCustomDnsOptions()
        }
    }

    fun onMoveCustomDnsDown(selectedCustomDns: SelectedCustomDns) {
        mainScope.launch {
            val newCustomDnsIndex = selectedCustomDns.index.plus(1)

            userSettingsManager.updateCustomDns { customDns ->
                customDns.copy(
                    rawDnsList = customDns.rawDnsList.swapOrCurrent(
                        index1 = selectedCustomDns.index,
                        index2 = newCustomDnsIndex,
                    )
                )
            }

            onCloseSelectedCustomDnsOptions()
        }
    }

    fun onDeleteCustomDns(selectedCustomDns: SelectedCustomDns) {
        mainScope.launch {
            userSettingsManager.updateCustomDns { customDns ->
                val newCustomDnsList = customDns.rawDnsList
                    .toMutableList()
                    .apply {
                        if (selectedCustomDns.index in indices) {
                            removeAt(index = selectedCustomDns.index)
                        }
                    }
                    .toList()

                customDns.copy(
                    toggleEnabled = newCustomDnsList.isNotEmpty(),
                    rawDnsList = newCustomDnsList,
                )
            }
        }

        onCloseSelectedCustomDnsOptions()

        onDismissDeleteCustomDnsDialog()
    }

    fun onCloseSelectedCustomDnsOptions() {
        selectedCustomDnsFlow.value = null
    }

    fun onShowDeleteCustomDnsDialog(selectedCustomDns: SelectedCustomDns) {
        dialogState = DialogState.Delete(
            index = selectedCustomDns.index,
            customDns = selectedCustomDns.customDns,
        )
    }

    fun onDismissDeleteCustomDnsDialog() {
        dialogState = null
    }

    fun onReconnectNow(vpnUiDelegate: VpnUiDelegate) {
        settingsReconnectHandler.onReconnectClicked(
            uiDelegate = vpnUiDelegate,
            dontShowAgain = false,
            type = DontShowAgainStore.Type.DnsChangeWhenConnected,
        )

        closeScreen()
    }

    fun onShowReconnectNowDialog(vpnUiDelegate: VpnUiDelegate) {
        viewModelScope.launch {
            settingsReconnectHandler.reconnectionCheck(
                uiDelegate = vpnUiDelegate,
                type = DontShowAgainStore.Type.DnsChangeWhenConnected,
            )

            if (settingsReconnectHandler.showReconnectDialogFlow.value == null) {
                closeScreen()
            }
        }
    }

    fun onDismissReconnectNowDialog() {
        settingsReconnectHandler.dismissReconnectDialog(
            dontShowAgain = false,
            type = DontShowAgainStore.Type.DnsChangeWhenConnected,
        )

        closeScreen()
    }

    private fun closeScreen() {
        viewModelScope.launch {
            eventChannel.send(Event.OnClose)
        }
    }

    private companion object {

        private const val DIALOG_STATE_KEY = "dialog_key"

    }

}
