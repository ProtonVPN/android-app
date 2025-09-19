/*
 * Copyright (c) 2025. Proton AG
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

package com.protonvpn.android.tv.settings.netshield

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.vpn.DnsOverride
import com.protonvpn.android.vpn.IsPrivateDnsActiveFlow
import com.protonvpn.android.vpn.getDnsOverride
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TvSettingsNetShieldViewModel @Inject constructor(
    isPrivateDnsActiveFlow: IsPrivateDnsActiveFlow,
    private val mainScope: CoroutineScope,
    private val userSettingsManager: CurrentUserLocalSettingsManager,
) : ViewModel() {

    data class ViewState(
        val isNetShieldEnabled: Boolean,
        val dnsOverride: DnsOverride,
    )

    val viewState: StateFlow<ViewState?> = combine(
        isPrivateDnsActiveFlow,
        userSettingsManager.rawCurrentUserSettingsFlow,
    ) { isPrivateDnsActive, localUserSettings ->
        ViewState(
            isNetShieldEnabled = localUserSettings.netShield != NetShieldProtocol.DISABLED,
            dnsOverride = getDnsOverride(
                isPrivateDnsActive = isPrivateDnsActive,
                effectiveSettings = localUserSettings,
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    fun toggleNetShield() {
        mainScope.launch {
            userSettingsManager.toggleNetShield()
        }
    }

    fun disableCustomDns() {
        mainScope.launch {
            userSettingsManager.disableCustomDNS()
        }
    }

}
