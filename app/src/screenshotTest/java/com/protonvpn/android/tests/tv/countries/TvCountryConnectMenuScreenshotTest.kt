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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.protonvpn.android.R
import com.protonvpn.android.annotations.ProtonVpnTvTestPreview
import com.protonvpn.android.base.ui.ProtonVpnTvPreview
import com.protonvpn.android.redesign.CityStateId
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.tv.countries.ui.CountryConnectMenu
import com.protonvpn.android.tv.countries.ui.CountryConnectMenuOption
import com.protonvpn.android.tv.countries.ui.CountryConnectMenuOption.Type
import com.protonvpn.android.tv.countries.ui.CountryConnectMenuUiState

@PreviewTest
@ProtonVpnTvTestPreview
@Composable
fun TvCountryConnectMenu_Connected_Screenshot() {
    MenuScreen(
        menuState("gb", "United Kingdom", R.drawable.flag_tv_gb, UK_CITIES) { option ->
            option.copy(connected = option.type == Type.Fastest)
        }
    )
}

@PreviewTest
@ProtonVpnTvTestPreview
@Composable
fun TvCountryConnectMenu_Maintenance_Screenshot() {
    MenuScreen(
        menuState("au", "Australia", R.drawable.flag_tv_au, AU_CITIES) { option ->
            option.copy(inMaintenance = (option.type as? Type.CityState)?.name == "Brisbane")
        }
    )
}

@PreviewTest
@ProtonVpnTvTestPreview
@Composable
fun TvCountryConnectMenu_FreeUser_Screenshot() {
    MenuScreen(
        menuState("us", "United States", R.drawable.flag_tv_us, US_STATES, isState = true) { option ->
            option.copy(available = option.type == Type.Fastest)
        }
    )
}

@PreviewTest
@ProtonVpnTvTestPreview
@Composable
fun TvCountryConnectMenu_LongCountryName_Screenshot() {
    MenuScreen(menuState("ae", "United Arab Emirates", R.drawable.flag_tv_ae, listOf("Dubai", "Fujairah")))
}

@Composable
private fun MenuScreen(state: CountryConnectMenuUiState) {
    ProtonVpnTvPreview {
        Box(contentAlignment = Alignment.CenterEnd, modifier = Modifier.fillMaxSize().padding(24.dp)) {
            CountryConnectMenu(state, onOptionClick = {}, modifier = Modifier.fillMaxHeight())
        }
    }
}

private val UK_CITIES = listOf("Belfast", "Cardiff", "Edinburgh", "Glasgow", "London", "Manchester")
private val AU_CITIES = listOf("Adelaide", "Brisbane", "Melbourne", "Perth", "Sydney")
private val US_STATES =
    listOf("Arizona", "California", "Colorado", "District Of Columbia", "Florida", "Georgia", "Illinois", "New York")

private fun menuState(
    countryCode: String,
    countryName: String,
    flagResource: Int,
    cities: List<String>,
    isState: Boolean = false,
    transform: (CountryConnectMenuOption) -> CountryConnectMenuOption = { it },
): CountryConnectMenuUiState {
    val types = listOf(Type.Fastest, Type.Random) +
        cities.map { Type.CityState(CityStateId(it, isState), it) }
    return CountryConnectMenuUiState(
        countryId = CountryId(countryCode),
        countryName = countryName,
        flagResource = flagResource,
        options = types.map { type ->
            transform(CountryConnectMenuOption(type, available = true, inMaintenance = false, connected = false))
        },
    )
}
