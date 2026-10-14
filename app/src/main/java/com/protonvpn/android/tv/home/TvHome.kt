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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.tv.countries.ui.CountriesGrid
import com.protonvpn.android.tv.countries.ui.CountryGridUiItem

@Composable
fun TvHomeRoute(
    viewModel: TvHomeViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val currentLocale = LocalConfiguration.current.locales.get(0)
    val countriesFlow = remember(currentLocale) {
        viewModel.observeCountries(currentLocale)
    }
    val countries = countriesFlow.collectAsStateWithLifecycle(initialValue = emptyList()).value
    TvHome(countries = countries, modifier = modifier)
}

@Composable
internal fun TvHome(
    countries: List<CountryGridUiItem>,
    modifier: Modifier = Modifier,
) {
    CountriesGrid(
        countries = countries,
        modifier = modifier
            .fillMaxSize()
            .padding(48.dp),
    )
}
