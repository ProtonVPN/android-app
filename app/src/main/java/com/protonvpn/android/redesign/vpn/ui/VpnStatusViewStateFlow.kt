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
import com.protonvpn.android.ui.promooffers.HomeScreenPromoBannerFlow
import com.protonvpn.android.ui.promooffers.PromoOfferBannerState
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStatusProviderUI
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.Locale
import javax.inject.Inject

class VpnStatusViewStateFlow(
    vpnStatusProvider: VpnStatusProviderUI,
    serverListUpdaterPrefs: ServerListUpdaterPrefs,
    vpnConnectionManager: VpnConnectionManager,
    effectiveCurrentUserSettings: EffectiveCurrentUserSettings,
    currentUser: CurrentUser,
    changeServerViewStateFlow: Flow<ChangeServerViewState?>,
    homeScreenPromoBannerFlow: Flow<PromoOfferBannerState?>
) : Flow<VpnStatusViewState> {

    @Inject
    constructor(
        vpnStatusProvider: VpnStatusProviderUI,
        serverListUpdaterPrefs: ServerListUpdaterPrefs,
        vpnConnectionManager: VpnConnectionManager,
        effectiveCurrentUserSettings: EffectiveCurrentUserSettings,
        currentUser: CurrentUser,
        changeServerViewStateFlow: ChangeServerViewStateFlow,
        homeScreenPromoBannerFlow: HomeScreenPromoBannerFlow,
    ) : this(
        vpnStatusProvider,
        serverListUpdaterPrefs,
        vpnConnectionManager,
        effectiveCurrentUserSettings,
        currentUser,
        changeServerViewStateFlow as Flow<ChangeServerViewState?>,
        homeScreenPromoBannerFlow as Flow<PromoOfferBannerState?>
    )

    private val locationTextFlow = combine(
        serverListUpdaterPrefs.ipAddressFlow,
        serverListUpdaterPrefs.lastKnownCountryFlow
    ) { ipAddress, country ->
        getLocationText(country, ipAddress)
    }

    private val bannerFlow: Flow<StatusBanner?> =
        combine(
            effectiveCurrentUserSettings.netShield,
            vpnConnectionManager.netShieldStats,
            currentUser.vpnUserFlow,
            changeServerViewStateFlow,
            homeScreenPromoBannerFlow.map { it != null },
        ) { state, stats, user, changeServer, hasPromoBanner ->
            val availability = user.getNetShieldAvailability()
            when {
                hasPromoBanner && availability != NetShieldAvailability.AVAILABLE -> null
                changeServer is ChangeServerViewState.Locked -> StatusBanner.UnwantedCountry
                else -> when (availability) {
                        NetShieldAvailability.AVAILABLE -> StatusBanner.NetShieldBanner(
                            NetShieldViewState(state, stats)
                        )

                        NetShieldAvailability.HIDDEN -> null
                        NetShieldAvailability.UPGRADE_VPN_PLUS -> StatusBanner.UpgradePlus
                    }
                }
        }

    private val vpnFlow = combine(
        vpnStatusProvider.uiStatus,
        locationTextFlow,
        bannerFlow,
    ) { status, locationText, statusBanner ->
        when (status.state) {
            VpnState.Connected -> {
                val connectionParams = status.connectionParams
                VpnStatusViewState.Connected(connectionParams!!.server.isSecureCoreServer, statusBanner)
            }
            VpnState.ScanningPorts, VpnState.CheckingAvailability, VpnState.Connecting, VpnState.Reconnecting -> {
                VpnStatusViewState.Connecting(locationText)
            }
            VpnState.WaitingForNetwork -> {
                VpnStatusViewState.WaitingForNetwork(locationText)
            }
            VpnState.Disconnecting, VpnState.Disabled -> {
                VpnStatusViewState.Disabled(locationText)
            }
            is VpnState.Error -> {
                if (status.state.isFinal)
                    VpnStatusViewState.Disabled()
                else
                    VpnStatusViewState.Connecting()
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
