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
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.countries.Translator
import com.protonvpn.android.redesign.countries.ui.ServerFilterType
import com.protonvpn.android.redesign.countries.ui.ServerListViewModelDataAdapter
import com.protonvpn.android.redesign.countries.ui.sortedForUi
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStatusProviderUI
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import java.util.Locale
import javax.inject.Inject

@Reusable
class CountryConnectMenuFlow @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val adapter: ServerListViewModelDataAdapter,
    private val translator: Translator,
    private val vpnStatusProviderUI: VpnStatusProviderUI,
    private val currentUser: CurrentUser,
) {

    // Emits null if the country has no servers.
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(countryId: CountryId, locale: Locale): Flow<CountryConnectMenuUiState?> = translator.flow
        .flatMapLatest { translations ->
            combine(
                adapter.countries(ServerFilterType.All, includeFreeServers = true)
                    .map { countries -> countries.find { it.countryId == countryId } },
                adapter.cities(ServerFilterType.All, countryId, translations, includeFreeServers = true),
                currentUser.vpnUserFlow.mapNotNull { it?.maxTier }.distinctUntilChanged(),
                vpnStatusProviderUI.uiStatus
                    .map { status -> status.connectIntent.takeIf { status.state == VpnState.Connected } }
                    .distinctUntilChanged(),
            ) { country, cities, userTier, connectedIntent ->
                if (country == null) return@combine null
                val countryAvailable = userTier >= country.tier
                // Free users can only connect to the fastest server in a country.
                val canChooseLocation = userTier > 0
                val fastest = CountryConnectMenuOption(
                    type = CountryConnectMenuOption.Type.Fastest,
                    available = countryAvailable,
                    inMaintenance = country.inMaintenance,
                    connected = connectedIntent is ConnectIntent.FastestInCountry &&
                        connectedIntent.country == countryId,
                )
                val random = CountryConnectMenuOption(
                    type = CountryConnectMenuOption.Type.Random,
                    available = countryAvailable && canChooseLocation,
                    inMaintenance = country.inMaintenance,
                    // TODO: set once there is a ConnectIntent for a random server in a country.
                    connected = false,
                )
                val cityOptions = cities.sortedForUi(locale).map { city ->
                    CountryConnectMenuOption(
                        type = CountryConnectMenuOption.Type.CityState(city.cityStateId, city.name),
                        available = canChooseLocation && userTier >= city.tier,
                        inMaintenance = city.inMaintenance,
                        connected = when (connectedIntent) {
                            is ConnectIntent.FastestInCity ->
                                !city.cityStateId.isState && connectedIntent.country == countryId &&
                                    connectedIntent.cityEn == city.cityStateId.name
                            is ConnectIntent.FastestInState ->
                                city.cityStateId.isState && connectedIntent.country == countryId &&
                                    connectedIntent.stateEn == city.cityStateId.name
                            else -> false
                        },
                    )
                }
                CountryConnectMenuUiState(
                    countryId = countryId,
                    countryName = CountryTools.getFullName(locale, countryId.countryCode),
                    flagResource = CountryTools.getLargeFlagResource(context, countryId.countryCode),
                    options = listOf(fastest, random) + cityOptions,
                )
            }
        }
}
