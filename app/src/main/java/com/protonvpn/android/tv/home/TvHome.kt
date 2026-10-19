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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.telemetry.UpgradeSource
import com.protonvpn.android.telemetry.UpgradeTrigger
import com.protonvpn.android.tv.countries.ui.CountriesGrid
import com.protonvpn.android.tv.countries.ui.CountryConnectMenuDialog
import com.protonvpn.android.tv.countries.ui.CountryGridUiItem
import com.protonvpn.android.tv.upsell.TvUpsellActivity
import com.protonvpn.android.tv.upsell.TvUpsellContent
import com.protonvpn.android.vpn.ui.LocalVpnUiDelegate
import kotlinx.coroutines.flow.flowOf

@Composable
fun TvHomeRoute(
    viewModel: TvHomeViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val vpnUiDelegate = LocalVpnUiDelegate.current
    val currentLocale = LocalConfiguration.current.locales.get(0)
    val countriesFlow = remember(currentLocale) {
        viewModel.observeCountries(currentLocale)
    }
    val countries = countriesFlow.collectAsStateWithLifecycle(initialValue = emptyList()).value

    var menuCountryCode by rememberSaveable { mutableStateOf<String?>(null) }
    val menuStateFlow = remember(menuCountryCode, currentLocale) {
        menuCountryCode?.let { viewModel.observeConnectMenu(CountryId(it), currentLocale) } ?: flowOf(null)
    }
    val menuState = menuStateFlow.collectAsStateWithLifecycle(initialValue = null).value

    val launchUpsell = { countryId: CountryId ->
        TvUpsellActivity.launch(
            context,
            TvUpsellContent.AllCountries,
            UpgradeSource.COUNTRIES,
            UpgradeTrigger.COUNTRY_SELECTION,
            countryId
        )
    }

    TvHome(
        countries = countries,
        onCountryClick = { country -> viewModel.onCountryClick(vpnUiDelegate, country, launchUpsell) },
        onCountryLongClick = { menuCountryCode = it.countryId.countryCode },
        modifier = modifier
    )

    if (menuState != null) {
        val closeMenu = { menuCountryCode = null }
        CountryConnectMenuDialog(
            state = menuState,
            onOptionClick = { option ->
                viewModel.onConnectMenuOptionClick(
                    vpnUiDelegate = vpnUiDelegate,
                    countryId = menuState.countryId,
                    option = option,
                    launchUpsell = launchUpsell,
                    closeMenu = closeMenu,
                )
            },
            onDismissRequest = closeMenu,
        )
    }
}

@Composable
internal fun TvHome(
    countries: List<CountryGridUiItem>,
    onCountryClick: (CountryGridUiItem) -> Unit,
    onCountryLongClick: (CountryGridUiItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    CountriesGrid(
        countries = countries,
        onCountryClick = onCountryClick,
        onCountryLongClick = onCountryLongClick,
        modifier = modifier
            .fillMaxSize(),
    )
}
