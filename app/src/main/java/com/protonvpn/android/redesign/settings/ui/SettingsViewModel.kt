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

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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

    sealed class SettingViewState<T>(
        val value: T,
        val isRestricted: Boolean = false,
        @StringRes val titleRes: Int,
        @StringRes val subtitleRes: Int,
        @DrawableRes val iconRes: Int
    ) {
        val upgradeIconRes = if (isRestricted) R.drawable.vpn_plus_badge else null
        class NetShieldSettingViewState(netShieldEnabled: Boolean, isFreeUser: Boolean) :
            SettingViewState<Boolean>(
                value = netShieldEnabled,
                isRestricted = isFreeUser,
                titleRes = R.string.netshield_feature_name,
                subtitleRes = if (netShieldEnabled) R.string.feature_on else R.string.feature_off,
                iconRes = if (netShieldEnabled) R.drawable.ic_netshield_on else R.drawable.ic_netshield_off
            )

        class SplitTunnelingSettingViewState(
            splitTunnelingEnabled: Boolean,
            isFreeUser: Boolean
        ) : SettingViewState<Boolean>(
            value = splitTunnelingEnabled,
            isRestricted = isFreeUser,
            titleRes = R.string.settings_split_tunneling_title,
            subtitleRes = if (splitTunnelingEnabled) R.string.feature_on else R.string.feature_off,
            iconRes = if (splitTunnelingEnabled) R.drawable.ic_split_tunneling_on else R.drawable.ic_split_tunneling_off
        )

        class VpnAcceleratorSettingViewState(
            vpnAcceleratorEnabled: Boolean,
            isFreeUser: Boolean
        ) : SettingViewState<Boolean>(
            value = vpnAcceleratorEnabled,
            isRestricted = isFreeUser,
            titleRes = R.string.settings_vpn_accelerator_title,
            subtitleRes = if (vpnAcceleratorEnabled) R.string.feature_on else R.string.feature_off,
            iconRes = me.proton.core.auth.R.drawable.ic_proton_rocket
        )

        class ProtocolSelectionViewState(
            protocol: ProtocolSelection,
        ) : SettingViewState<ProtocolSelection>(
            value = protocol,
            isRestricted = false,
            titleRes = R.string.settings_protocol_title,
            subtitleRes = protocol.displayName,
            iconRes = me.proton.core.auth.R.drawable.ic_proton_servers
        )
    }

    data class SettingsViewState(
        val netShieldSettingViewState: SettingViewState<Boolean>,
        val splitTunnelingViewState: SettingViewState<Boolean>,
        val vpnAcceleratorViewState: SettingViewState<Boolean>,
        val currentProtocolSelection: SettingViewState<ProtocolSelection>,
        val userInfo: UserViewState
    )
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
                netShieldSettingViewState = SettingViewState.NetShieldSettingViewState(settings.netShield != NetShieldProtocol.DISABLED, isFree),
                vpnAcceleratorViewState = SettingViewState.VpnAcceleratorSettingViewState(settings.vpnAccelerator, isFree),
                splitTunnelingViewState =  SettingViewState.SplitTunnelingSettingViewState(settings.splitTunneling.isEnabled, isFree),
                currentProtocolSelection = SettingViewState.ProtocolSelectionViewState(settings.protocol),
                userInfo = userViewState,
            )
        }

    private fun getInitials(name: String?): String? {
        return name?.split(" ")
            ?.take(2) // UI does not support 3 chars initials
            ?.mapNotNull { it.firstOrNull()?.uppercase() }
            ?.joinToString("")
    }

    fun toggleNetShield() {
        viewModelScope.launch {
            userSettingsManager.toggleNetShield()
        }
    }

    fun updateProtocol(protocol: ProtocolSelection) {
        viewModelScope.launch {
            userSettingsManager.updateProtocol(protocol)
        }
    }

    val vpnAcceleratorValue = viewState.map { it.vpnAcceleratorViewState }.distinctUntilChanged()
    val netShieldValue = viewState.map { it.netShieldSettingViewState }.distinctUntilChanged()

    fun toggleVpnAccelerator() {
        viewModelScope.launch {
            userSettingsManager.toggleVpnAccelerator()
        }
    }
}
