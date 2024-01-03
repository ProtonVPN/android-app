/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.android.redesign.vpn.ui

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.netshield.NetShieldAvailability
import com.protonvpn.android.netshield.NetShieldViewState
import com.protonvpn.android.netshield.getNetShieldAvailability
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.ui.home.ServerListUpdaterPrefs
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStatusProviderUI
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.combine
import java.util.Locale
import javax.inject.Inject

class VpnStatusViewStateFlow @Inject constructor(
    vpnStatusProvider: VpnStatusProviderUI,
    serverListUpdaterPrefs: ServerListUpdaterPrefs,
    vpnConnectionManager: VpnConnectionManager,
    effectiveCurrentUserSettings: EffectiveCurrentUserSettings,
    currentUser: CurrentUser
) : Flow<VpnStatusViewState> {


    private val netShieldViewState: Flow<NetShieldViewState> =
        combine(
            effectiveCurrentUserSettings.netShield,
            vpnConnectionManager.netShieldStats,
            currentUser.vpnUserFlow
        ) { state, stats, user ->
            when(user.getNetShieldAvailability()) {
                NetShieldAvailability.AVAILABLE -> NetShieldViewState.NetShieldState(state, stats)
                NetShieldAvailability.UPGRADE_VPN_BUSINESS -> NetShieldViewState.UpgradeBusinessBanner
                NetShieldAvailability.UPGRADE_VPN_PLUS -> NetShieldViewState.UpgradePlusBanner
            }
        }


    private val vpnFlow = combine(
        vpnStatusProvider.status,
        serverListUpdaterPrefs.ipAddressFlow,
        serverListUpdaterPrefs.lastKnownCountryFlow,
        netShieldViewState,
        currentUser.vpnUserFlow,
    ) { status, ipAddress, country, netShieldStats, user ->
        when (status.state) {
            VpnState.Connected -> {
                val connectionParams = status.connectionParams
                VpnStatusViewState.Connected(connectionParams!!.server.isSecureCoreServer, netShieldStats)
            }
            VpnState.ScanningPorts, VpnState.CheckingAvailability, VpnState.Connecting, VpnState.Reconnecting -> {
                VpnStatusViewState.Connecting(getLocationText(country, ipAddress))
            }
            VpnState.WaitingForNetwork -> {
                VpnStatusViewState.WaitingForNetwork(getLocationText(country, ipAddress))
            }
            VpnState.Disconnecting, VpnState.Disabled -> {
                VpnStatusViewState.Disabled(getLocationText(country, ipAddress))
            }
            is VpnState.Error -> {
                VpnStatusViewState.Disabled()
            }
        }
    }

    private fun getLocationText(country: String, ip: String): LocationText? {
        if (country.isEmpty() || ip.isEmpty()) return null
        val countryName = CountryTools.getFullName(Locale.getDefault(), country)

        return LocationText(countryName, ip)
    }

    override suspend fun collect(collector: FlowCollector<VpnStatusViewState>) {
        vpnFlow.collect(collector)
    }
}
