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
import com.protonvpn.android.redesign.settings.ui.SettingsViewModel.ProfileOverrideInfo
import com.protonvpn.android.redesign.settings.ui.SettingsViewModel.SettingViewState
import com.protonvpn.android.redesign.vpn.ui.GetConnectIntentViewState
import com.protonvpn.android.redesign.vpn.usecases.SettingsForConnection
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.vpn.DnsOverride
import com.protonvpn.android.vpn.DnsOverrideFlow
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStatusProviderUI
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CustomDnsViewModel @Inject constructor(
    private val userSettingsManager: CurrentUserLocalSettingsManager,
    private val getConnectIntentViewState: GetConnectIntentViewState,
    settingsForConnection: SettingsForConnection,
    vpnStatusProviderUI: VpnStatusProviderUI,
    dnsOverrideFlow: DnsOverrideFlow,
    currentUser: CurrentUser,
) : ViewModel() {

    val isConnected = vpnStatusProviderUI.uiStatus.map {
        it.state is VpnState.Connected
    }

    val dnsViewStateFlow = combine(
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

    fun addNewDns(newDns: String) {
        viewModelScope.launch {
            val currentSettings = userSettingsManager.rawCurrentUserSettingsFlow.first()
            val currentList = currentSettings.customDns.rawDnsList
            val updatedDnsList = currentList.toMutableList().apply {
                add(newDns)
            }
            userSettingsManager.updateCustomDnsList(updatedDnsList)
        }
    }

    fun removeDnsItem(item: String) {
        viewModelScope.launch {
            val currentList = userSettingsManager.rawCurrentUserSettingsFlow.first().customDns.rawDnsList
            userSettingsManager.updateCustomDnsList(currentList - item)
        }
    }

    fun undoRemoval(undoData: DnsSettingsDataSource.UndoSnackbarData) {
        viewModelScope.launch {
            userSettingsManager.updateCustomDns { current ->
                val newList = current.rawDnsList.toMutableList()
                val safePosition = minOf(undoData.position, newList.size)

                newList.add(safePosition, undoData.removedItem)
                current.copy(rawDnsList = newList)
            }
        }
    }

    fun updateCustomDnsList(newDnsList: List<String>) {
        viewModelScope.launch {
            userSettingsManager.updateCustomDnsList(newDnsList)
        }
    }

    fun toggleCustomDns() {
        viewModelScope.launch {
            userSettingsManager.toggleCustomDNS()
        }
    }
} 