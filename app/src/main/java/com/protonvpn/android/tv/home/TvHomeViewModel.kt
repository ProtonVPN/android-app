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

package com.protonvpn.android.tv.home

import androidx.lifecycle.ViewModel
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.UiConnect
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.tv.countries.ui.CountriesGridFlow
import com.protonvpn.android.tv.countries.ui.CountryConnectMenuFlow
import com.protonvpn.android.tv.countries.ui.CountryConnectMenuOption
import com.protonvpn.android.tv.countries.ui.CountryConnectMenuUiState
import com.protonvpn.android.tv.countries.ui.CountryGridUiItem
import com.protonvpn.android.tv.usecases.TvUiConnectDisconnectHelper
import com.protonvpn.android.tv.vpn.ui.VpnStatusTvFlow
import com.protonvpn.android.tv.vpn.ui.VpnStatusTvViewState
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.DisconnectTrigger
import com.protonvpn.android.vpn.VpnConnect
import com.protonvpn.android.vpn.VpnUiDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class TvHomeViewModel @Inject constructor(
    private val countriesGridFlow: CountriesGridFlow,
    private val countryConnectMenuFlow: CountryConnectMenuFlow,
    private val connect: VpnConnect,
    private val connectDisconnectHelper: TvUiConnectDisconnectHelper,
    private val vpnStatusTvFlow: VpnStatusTvFlow,
) : ViewModel() {

    fun observeVpnStatus(locale: Locale): Flow<VpnStatusTvViewState?> = vpnStatusTvFlow.observe(locale)

    fun observeCountries(locale: Locale): Flow<List<CountryGridUiItem>> =
        countriesGridFlow.observeCountries(locale)

    fun observeConnectMenu(countryId: CountryId, locale: Locale): Flow<CountryConnectMenuUiState?> =
        countryConnectMenuFlow.observe(countryId, locale)

    fun onVpnStatusActionClick(vpnUiDelegate: VpnUiDelegate, status: VpnStatusTvViewState.Status) {
        when (status) {
            VpnStatusTvViewState.Status.Unprotected -> {
                val trigger = ConnectTrigger.QuickConnect("status (TV)")
                ProtonLogger.log(UiConnect, trigger.description)
                connect(vpnUiDelegate, ConnectIntent.Fastest, trigger)
            }
            VpnStatusTvViewState.Status.Connecting, VpnStatusTvViewState.Status.Protected ->
                connectDisconnectHelper.disconnect(DisconnectTrigger.QuickConnect("status (TV)"))
        }
    }

    fun onCountryClick(
        vpnUiDelegate: VpnUiDelegate,
        country: CountryGridUiItem,
        launchUpsell: (CountryId) -> Unit,
    ) {
        when {
            country.status == CountryGridUiItem.Status.InMaintenance -> {
                // TODO: show the maintenance error dialog.
            }
            !country.available -> launchUpsell(country.countryId)
            else -> {
                val trigger = ConnectTrigger.CountriesCountry("country grid (TV)")
                ProtonLogger.log(UiConnect, trigger.description)
                connect(vpnUiDelegate, ConnectIntent.FastestInCountry(country.countryId, emptySet()), trigger)
            }
        }
    }

    fun onConnectMenuOptionClick(
        vpnUiDelegate: VpnUiDelegate,
        countryId: CountryId,
        option: CountryConnectMenuOption,
        launchUpsell: (CountryId) -> Unit,
        closeMenu: () -> Unit,
    ) {
        when {
            option.inMaintenance -> Unit
            !option.available -> launchUpsell(countryId)
            option.connected -> {
                connectDisconnectHelper.disconnect(DisconnectTrigger.Country("country menu (TV)"))
                closeMenu()
            }
            else -> {
                val connectIntent = option.type.connectIntent(countryId) ?: return
                val trigger = option.type.connectTrigger()
                ProtonLogger.log(UiConnect, trigger.description)
                connect(vpnUiDelegate, connectIntent, trigger)
                closeMenu()
            }
        }
    }

    private fun CountryConnectMenuOption.Type.connectIntent(countryId: CountryId): ConnectIntent? =
        when (this) {
            CountryConnectMenuOption.Type.Fastest -> ConnectIntent.FastestInCountry(countryId, emptySet())
            // TODO: connect once there is a ConnectIntent for a random server in a country.
            CountryConnectMenuOption.Type.Random -> null
            is CountryConnectMenuOption.Type.CityState ->
                if (cityStateId.isState) ConnectIntent.FastestInState(countryId, cityStateId.name, emptySet())
                else ConnectIntent.FastestInCity(countryId, cityStateId.name, emptySet())
        }

    private fun CountryConnectMenuOption.Type.connectTrigger(): ConnectTrigger = when (this) {
        CountryConnectMenuOption.Type.Fastest -> ConnectTrigger.CountriesCountry("country menu (TV): fastest")
        CountryConnectMenuOption.Type.Random -> ConnectTrigger.CountriesCountry("country menu (TV): random")
        is CountryConnectMenuOption.Type.CityState ->
            if (cityStateId.isState) ConnectTrigger.CountriesState("country menu (TV): state")
            else ConnectTrigger.CountriesCity("country menu (TV): city")
    }
}
