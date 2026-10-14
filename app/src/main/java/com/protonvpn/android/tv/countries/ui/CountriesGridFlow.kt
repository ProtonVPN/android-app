/*
 * Copyright (c) 2026. Proton AG
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

package com.protonvpn.android.tv.countries.ui

import android.content.Context
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.redesign.countries.ui.ServerFilterType
import com.protonvpn.android.redesign.countries.ui.ServerListViewModelDataAdapter
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.sortedByLocaleAware
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStatusProviderUI
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapNotNull
import java.util.Locale
import javax.inject.Inject

@Reusable
class CountriesGridFlow @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val adapter: ServerListViewModelDataAdapter,
    private val vpnStatusProviderUI: VpnStatusProviderUI,
    private val currentUser: CurrentUser,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeCountries(locale: Locale): Flow<List<CountryGridUiItem>> = currentUser.vpnUserFlow
        .mapNotNull { it?.maxTier }
        .distinctUntilChanged()
        .flatMapLatest { userTier ->
            combine(
                adapter.countries(ServerFilterType.All, includeFreeServers = true),
                vpnStatusProviderUI.uiStatus
            ) { countries, vpnStatus ->
                val isConnecting = vpnStatus.state == VpnState.Connecting
                val isConnected = vpnStatus.state == VpnState.Connected

                countries.map { country ->
                    val flagResource = CountryTools.getLargeFlagResource(context, country.countryId.countryCode)
                    val countryName = CountryTools.getFullName(Locale.getDefault(), country.countryId.countryCode)
                    val isAvailable = userTier >= country.tier

                    CountryGridUiItem(
                        countryId = country.countryId,
                        countryName = countryName,
                        flagResource = flagResource,
                        status = if (country.inMaintenance) {
                            CountryGridUiItem.Status.InMaintenance
                        } else {
                            CountryGridUiItem.Status.Regular
                        },
                        available = isAvailable,
                        tier = country.tier,
                    )
                }.sortedByLocaleAware(locale) { it.countryName }
            }
        }
}
