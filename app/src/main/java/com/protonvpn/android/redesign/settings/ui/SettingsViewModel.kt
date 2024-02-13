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
import com.protonvpn.android.R
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.auth.usecase.uiName
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.userstorage.ProfileManager
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnStatusProviderUI
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val currentUser: CurrentUser,
    val userSettingsManager: CurrentUserLocalSettingsManager,
    val vpnConnectionManager: VpnConnectionManager,
    val vpnStatusProviderUI: VpnStatusProviderUI,
    val appConfig: AppConfig,
    val profileManager: ProfileManager,
) : ViewModel() {

    sealed class SettingValue<T>(val value: T) {
        class Restricted<T>(value: T) : SettingValue<T>(value)
        class Available<T>(value: T) : SettingValue<T>(value)

        val restricted get() = this is Restricted

        companion object {
            fun <T> fromValue(value: T, restricted: Boolean, isUserRestricted: Boolean) =
                if (restricted && isUserRestricted) Restricted(value) else Available(value)
        }
    }

    data class SettingsViewState(
        val netshieldEnabled: SettingValue<Boolean>,
        val splitTunnelingEnabled: SettingValue<Boolean>,
        val vpnAcceleratorEnabled: SettingValue<Boolean>,
        val currentProtocolSelection: ProtocolSelection,
        val userInfo: UserViewState
    ) {
        fun <T> restrictIconOrNull(value: SettingValue<T>) =
            if (userInfo.isFreeUser && value is SettingValue.Restricted)
                R.drawable.vpn_plus_badge else null
    }

    data class UserViewState(
        val isFreeUser: Boolean,
        val shortenedName: String,
        val displayName: String,
        val email: String
    )

    private val userViewStateFlow = combine(
        currentUser.vpnUserFlow,
        currentUser.userFlow.filterNotNull()
    ) { vpnUser, user ->
        UserViewState(
            shortenedName = getInitials(user.uiName()) ?: "",
            displayName = user.uiName() ?: "",
            email = user.email ?: "",
            isFreeUser = vpnUser?.isFreeUser == true
        )
    }

    val viewState =
        combine(
            userViewStateFlow,
            userSettingsManager.rawCurrentUserSettingsFlow
        ) { userViewState, settings ->
            val isFree = userViewState.isFreeUser
            SettingsViewState(
                netshieldEnabled = SettingValue.fromValue(settings.netShield != NetShieldProtocol.DISABLED, true, isFree),
                vpnAcceleratorEnabled = SettingValue.fromValue(settings.vpnAccelerator, true, isFree),
                splitTunnelingEnabled =  SettingValue.fromValue(settings.splitTunneling.isEnabled, true, isFree),
                currentProtocolSelection = settings.protocol,
                userInfo = userViewState,
            )
        }

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

    val vpnAcceleratorValue = viewState.map { it.vpnAcceleratorEnabled }.distinctUntilChanged()
    fun toggleVpnAccelerator() {
        viewModelScope.launch {
            userSettingsManager.toggleVpnAccelerator()
        }
    }
}
