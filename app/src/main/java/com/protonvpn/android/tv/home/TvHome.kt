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
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
import com.protonvpn.android.tv.vpn.ui.VpnStatusTv
import com.protonvpn.android.tv.vpn.ui.VpnStatusTvViewState
import com.protonvpn.android.vpn.ui.LocalVpnUiDelegate
import kotlinx.coroutines.flow.flowOf

@Composable
fun TvHomeRoute(
    drawerFocusRequester: FocusRequester,
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
    val vpnStatusFlow = remember(currentLocale) {
        viewModel.observeVpnStatus(currentLocale)
    }
    val vpnStatus = vpnStatusFlow.collectAsStateWithLifecycle(initialValue = null).value

    val menuState = menuStateFlow.collectAsStateWithLifecycle(initialValue = null).value

    val launchUpsell = { countryId: CountryId ->
        TvUpsellActivity.launch(
            context,
            TvUpsellContent.Country(countryId),
            UpgradeSource.COUNTRIES,
            UpgradeTrigger.COUNTRY_SELECTION,
            countryId
        )
    }

    if (vpnStatus != null) {
        TvHome(
            vpnStatus = vpnStatus,
            onVpnStatusActionClick = { status -> viewModel.onVpnStatusActionClick(vpnUiDelegate, status) },
            drawerFocusRequester = drawerFocusRequester,
            countries = countries,
            onCountryClick = { country -> viewModel.onCountryClick(vpnUiDelegate, country, launchUpsell) },
            onCountryLongClick = { menuCountryCode = it.countryId.countryCode },
            modifier = modifier
        )
    }

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
    vpnStatus: VpnStatusTvViewState,
    onVpnStatusActionClick: (VpnStatusTvViewState.Status) -> Unit,
    drawerFocusRequester: FocusRequester,
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
        header = {
            VpnStatusTv(
                vpnStatus,
                onActionClick = { onVpnStatusActionClick(vpnStatus.status) },
                // The button is aligned with the flags, but the tiles' focus bounds are wider than the flags
                // and start further left. Without this, moving left from the button would focus the first
                // tile instead of the drawer.
                actionStartFocusRequester = drawerFocusRequester,
                modifier = Modifier.padding(vertical = 60.dp),
            )
        },
    )
}
