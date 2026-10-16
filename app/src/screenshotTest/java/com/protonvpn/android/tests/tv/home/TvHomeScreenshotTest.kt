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

package com.protonvpn.android.tests.tv.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.protonvpn.android.R
import com.protonvpn.android.annotations.ProtonVpnTvTestPreview
import com.protonvpn.android.base.ui.ProtonVpnTvPreview
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.tv.countries.ui.CountryGridUiItem
import com.protonvpn.android.tv.countries.ui.CountryGridUiItem.Status
import com.protonvpn.android.tv.home.TvHome
import com.protonvpn.android.tv.vpn.ui.VpnStatusBackgroundTv

@PreviewTest
@ProtonVpnTvTestPreview
@Composable
fun TvHome_PlusUser_Screenshot() {
    TvHomeTest(
        countries = createCountries(available = true).mapIndexed { index, country ->
            when (index) {
                1 -> country.copy(status = Status.Connected)
                4 -> country.copy(status = Status.InMaintenance)
                else -> country
            }
        }
    )
}

@PreviewTest
@ProtonVpnTvTestPreview
@Composable
fun TvHome_FreeUser_Screenshot() {
    TvHomeTest(countries = createCountries(available = false))
}

@PreviewTest
@ProtonVpnTvTestPreview
@Composable
fun TvHome_PartialRow_Screenshot() {
    TvHomeTest(countries = createCountries(available = true).take(3))
}

@Composable
private fun TvHomeTest(countries: List<CountryGridUiItem>) {
    ProtonVpnTvPreview {
        // Same layering as in TvMainScreen: the status background is drawn behind the drawer and the content.
        Box(Modifier.fillMaxSize()) {
            VpnStatusBackgroundTv(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxSize()) {
                // Placeholder for the collapsed navigation drawer.
                Box(
                    Modifier
                        .width(80.dp)
                        .fillMaxHeight()
                )
                TvHome(countries = countries, modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun createCountries(available: Boolean): List<CountryGridUiItem> = listOf(
    "at" to "Austria" to R.drawable.flag_tv_at,
    "au" to "Australia" to R.drawable.flag_tv_au,
    "be" to "Belgium" to R.drawable.flag_tv_be,
    "br" to "Brazil" to R.drawable.flag_tv_br,
    "ca" to "Canada" to R.drawable.flag_tv_ca,
    "ch" to "Switzerland" to R.drawable.flag_tv_ch,
    "de" to "Germany" to R.drawable.flag_tv_de,
    "dk" to "Denmark" to R.drawable.flag_tv_dk,
    "es" to "Spain" to R.drawable.flag_tv_es,
    "fi" to "Finland" to R.drawable.flag_tv_fi,
    "fr" to "France" to R.drawable.flag_tv_fr,
    "gb" to "United Kingdom" to R.drawable.flag_tv_gb,
    "it" to "Italy" to R.drawable.flag_tv_it,
    "jp" to "Japan" to R.drawable.flag_tv_jp,
    "nl" to "Netherlands" to R.drawable.flag_tv_nl,
    "no" to "Norway" to R.drawable.flag_tv_no,
    "pl" to "Poland" to R.drawable.flag_tv_pl,
    "se" to "Sweden" to R.drawable.flag_tv_se,
).map { (codeAndName, flag) ->
    val (code, name) = codeAndName
    CountryGridUiItem(
        countryId = CountryId(code),
        countryName = name,
        flagResource = flag,
        status = Status.Regular,
        available = available,
        tier = if (available) 0 else 2,
        cityStateCount = 1,
        hasStates = false,
    )
}
