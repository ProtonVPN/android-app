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

package com.protonvpn.android.tests.tv.countries

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.protonvpn.android.R
import com.protonvpn.android.annotations.ProtonVpnTvTestPreview
import com.protonvpn.android.base.ui.ProtonVpnTvPreview
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.tv.countries.ui.CountriesGrid
import com.protonvpn.android.tv.countries.ui.CountryGridUiItem
import com.protonvpn.android.tv.countries.ui.CountryGridUiItem.Status

@PreviewTest
@ProtonVpnTvTestPreview
@Composable
fun TvCountriesGrid_MixedStates_Screenshot() {
    ProtonVpnTvPreview {
        CountriesGrid(countries = createMixedStateCountries())
    }
}

@PreviewTest
@ProtonVpnTvTestPreview
@Composable
fun TvCountriesGrid_AllUnavailable_Screenshot() {
    ProtonVpnTvPreview {
        CountriesGrid(countries = createUnavailableCountries())
    }
}

@PreviewTest
@ProtonVpnTvTestPreview
@Composable
fun TvCountriesGrid_WithMaintenance_Screenshot() {
    ProtonVpnTvPreview {
        CountriesGrid(countries = createMaintenanceCountries())
    }
}

@Composable
private fun createMixedStateCountries(): List<CountryGridUiItem> = listOf(
    CountryGridUiItem(
        countryId = CountryId("no"),
        countryName = "Norway",
        flagResource = R.drawable.flag_tv_no,
        status = Status.Regular,
        available = true,
        tier = 0,
    ),
    CountryGridUiItem(
        countryId = CountryId("us"),
        countryName = "United States",
        flagResource = R.drawable.flag_tv_us,
        status = Status.Connected,
        available = true,
        tier = 0,
    ),
    CountryGridUiItem(
        countryId = CountryId("de"),
        countryName = "Germany",
        flagResource = R.drawable.flag_tv_de,
        status = Status.Connecting,
        available = true,
        tier = 0,
    ),
    CountryGridUiItem(
        countryId = CountryId("fr"),
        countryName = "France",
        flagResource = R.drawable.flag_tv_fr,
        status = Status.InMaintenance,
        available = true,
        tier = 0,
    ),
    CountryGridUiItem(
        countryId = CountryId("jp"),
        countryName = "Japan",
        flagResource = R.drawable.flag_tv_jp,
        status = Status.Regular,
        available = true,
        tier = 0,
    ),
    CountryGridUiItem(
        countryId = CountryId("uk"),
        countryName = "United Kingdom",
        flagResource = R.drawable.flag_tv_gb,
        status = Status.Regular,
        available = true,
        tier = 0,
    ),
)

@Composable
private fun createUnavailableCountries(): List<CountryGridUiItem> = listOf(
    CountryGridUiItem(
        countryId = CountryId("no"),
        countryName = "Norway",
        flagResource = R.drawable.flag_tv_no,
        status = Status.Regular,
        available = false,
        tier = 2,
    ),
    CountryGridUiItem(
        countryId = CountryId("us"),
        countryName = "United States",
        flagResource = R.drawable.flag_tv_us,
        status = Status.Regular,
        available = false,
        tier = 2,
    ),
    CountryGridUiItem(
        countryId = CountryId("de"),
        countryName = "Germany",
        flagResource = R.drawable.flag_tv_de,
        status = Status.Regular,
        available = false,
        tier = 2,
    ),
    CountryGridUiItem(
        countryId = CountryId("fr"),
        countryName = "France",
        flagResource = R.drawable.flag_tv_fr,
        status = Status.Regular,
        available = false,
        tier = 2,
    ),
)

@Composable
private fun createMaintenanceCountries(): List<CountryGridUiItem> = listOf(
    CountryGridUiItem(
        countryId = CountryId("no"),
        countryName = "Norway",
        flagResource = R.drawable.flag_tv_no,
        status = Status.InMaintenance,
        available = true,
        tier = 0,
    ),
    CountryGridUiItem(
        countryId = CountryId("us"),
        countryName = "United States",
        flagResource = R.drawable.flag_tv_us,
        status = Status.InMaintenance,
        available = true,
        tier = 0,
    ),
    CountryGridUiItem(
        countryId = CountryId("de"),
        countryName = "Germany",
        flagResource = R.drawable.flag_tv_de,
        status = Status.InMaintenance,
        available = true,
        tier = 0,
    ),
    CountryGridUiItem(
        countryId = CountryId("fr"),
        countryName = "France",
        flagResource = R.drawable.flag_tv_fr,
        status = Status.InMaintenance,
        available = true,
        tier = 0,
    ),
)
