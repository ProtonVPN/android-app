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

package com.protonvpn.android.redesign.settings.ui.customdns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.redesign.settings.ui.SettingsViewModel.ProfileOverrideInfo
import com.protonvpn.android.redesign.settings.ui.SettingsViewModel.SettingViewState
import com.protonvpn.android.redesign.settings.ui.customdns.DnsSettingViewModelHelper.Event
import com.protonvpn.android.redesign.vpn.ui.GetConnectIntentViewState
import com.protonvpn.android.redesign.vpn.usecases.SettingsForConnection
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.vpn.DnsOverride
import com.protonvpn.android.vpn.DnsOverrideFlow
import com.protonvpn.android.vpn.VpnStatusProviderUI
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CustomDnsViewModel @Inject constructor(
    private val mainScope: CoroutineScope,
    private val userSettingsManager: CurrentUserLocalSettingsManager,
    private val getConnectIntentViewState: GetConnectIntentViewState,
    private val settingsForConnection: SettingsForConnection,
    vpnStatusProviderUI: VpnStatusProviderUI,
    dnsOverrideFlow: DnsOverrideFlow,
    currentUser: CurrentUser,
) : ViewModel() {

    val customDnsHelper = DnsSettingViewModelHelper(
        viewModelScope,
        combine(
            currentUser.jointUserFlow,
            settingsForConnection.getFlowForCurrentConnection(),
            dnsOverrideFlow,
        ) { user, connectionSettings, dnsOverride ->
            val isFree = user?.vpnUser?.isFreeUser == true
            val customDnsSetting = connectionSettings.connectionSettings.customDns
            val profileOverrideInfo = connectionSettings.associatedProfile?.let { profile ->
                val intentView = getConnectIntentViewState.forProfile(profile)
                ProfileOverrideInfo(
                    primaryLabel = intentView.primaryLabel,
                    profileName = profile.info.name
                )
            }

            SettingViewState.CustomDns(
                enabled = customDnsSetting.enabled,
                customDns = customDnsSetting.rawDnsList,
                overrideProfilePrimaryLabel = profileOverrideInfo?.primaryLabel,
                isFreeUser = isFree,
                isPrivateDnsActive = dnsOverride == DnsOverride.SystemPrivateDns
            )
        }
    )

    init {
        customDnsHelper.customDnsSettingState
            .filterNotNull()
            .map { state -> if (state.dnsViewState.value) state.dnsViewState.customDns else emptyList() }
            .distinctUntilChanged()
            .drop(1)
            .onEach {
                if (vpnStatusProviderUI.isConnected)
                    customDnsHelper.events.trySend(Event.CustomDnsSettingChangedWhenConnected)
            }
            .launchIn(viewModelScope)
    }

    fun addNewDns(newDns: String) {
        mainScope.launch {
            val currentSettings = userSettingsManager.rawCurrentUserSettingsFlow.first()
            val currentList = currentSettings.customDns.rawDnsList
            userSettingsManager.updateCustomDnsList(currentList + newDns)
        }
    }

    suspend fun removeDnsItem(item: String): UndoCustomDnsRemove? =
        mainScope.async {
            val currentList = userSettingsManager.rawCurrentUserSettingsFlow.first().customDns.rawDnsList
            val position = currentList.indexOf(item)
            if (position != -1) {
                userSettingsManager.updateCustomDnsList(currentList - item)
                GlobalUndoCustomDnsRemove(item, position)
            } else
                null
        }.await()

    private fun undoRemoval(removedItem: String, position: Int) {
        mainScope.launch {
            userSettingsManager.updateCustomDns { current ->
                val newList = current.rawDnsList.toMutableList()
                val safePosition = minOf(position, newList.size)

                newList.add(safePosition, removedItem)
                current.copy(rawDnsList = newList)
            }
        }
    }

    fun updateCustomDnsList(newDnsList: List<String>) {
        mainScope.launch {
            userSettingsManager.updateCustomDnsList(newDnsList)
        }
    }

    fun toggleCustomDns() {
        mainScope.launch {
            userSettingsManager.toggleCustomDNS()
        }
    }

    fun validateAndAddDnsAddress(dns: String) {
        viewModelScope.launch {
            val connectionSettings =  settingsForConnection.getFlowForCurrentConnection().first().connectionSettings
            val netShieldConflict = connectionSettings.netShield != NetShieldProtocol.DISABLED
                && connectionSettings.customDns.effectiveDnsList.isEmpty()
            customDnsHelper.validateAndAddDnsAddress(dns, netShieldConflict) {
                addNewDns(dns.trim())
            }
        }
    }

    private inner class GlobalUndoCustomDnsRemove(
        removedItem: String,
        position: Int
    ) : UndoCustomDnsRemove(removedItem, position) {
        override fun invoke() {
            undoRemoval(removedItem, position)
        }
    }
} 