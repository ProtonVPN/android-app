/*
 * Copyright (c) 2023 Proton Technologies AG
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
package com.protonvpn.android.redesign.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.appconfig.RestrictionsConfig
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.auth.usecase.uiName
import com.protonvpn.android.components.InstalledAppsProvider
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.ui.settings.BuildConfigInfo
import com.protonvpn.android.userstorage.ProfileManager
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnStatusProviderUI
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val currentUser: CurrentUser,
    val userSettings: EffectiveCurrentUserSettings,
    val userSettingsManager: CurrentUserLocalSettingsManager,
    val vpnConnectionManager: VpnConnectionManager,
    val vpnStatusProviderUI: VpnStatusProviderUI,
    val connectionManager: VpnConnectionManager,
    val appConfig: AppConfig,
    val installedAppsProvider: InstalledAppsProvider,
    val profileManager: ProfileManager,
    val buildConfigInfo: BuildConfigInfo,
    val restrictionsConfig: RestrictionsConfig
) : ViewModel() {

    data class SettingsViewState(
        val netshieldState: NetShieldProtocol,
        val userInfo: UserViewState
        )

    data class UserViewState(
        val shortenedName: String,
        val displayName: String,
        val email: String
    )

    val viewState =
        combine(currentUser.vpnUserFlow, currentUser.userFlow.filterNotNull(), userSettings.netShield) { vpnUser, user, netshield ->
            SettingsViewState(
                netshield,
                UserViewState(
                    getInitials(user.uiName()) ?: "",
                    user.uiName() ?: "",
                    user.email ?: ""
                )
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = SettingsViewState(
                NetShieldProtocol.DISABLED,
                UserViewState(
                    "",
                    "",
                    ""
                )
            )
        )

    private fun getInitials(name: String?): String? {
        return name?.split(" ")
            ?.take(2) // UI does not support 3 chars initials
            ?.mapNotNull { it.firstOrNull()?.uppercase() }
            ?.joinToString("")
    }

    fun setNetShieldProtocol(netShieldProtocol: NetShieldProtocol) {
        viewModelScope.launch {
            userSettingsManager.updateNetShield(netShieldProtocol)
        }
    }

    fun updateProtocol(protocol: ProtocolSelection) {
        viewModelScope.launch {
            userSettingsManager.updateProtocol(protocol)
        }
    }
}
