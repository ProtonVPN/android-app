/*
 * Copyright (c) 2023 Proton AG
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

package com.protonvpn.android.redesign.countries.ui.nav

import com.protonvpn.android.redesign.base.ui.nav.SafeNavGraphBuilder
import com.protonvpn.android.redesign.base.ui.nav.Screen
import com.protonvpn.android.redesign.base.ui.nav.ScreenNoArg
import com.protonvpn.android.redesign.base.ui.nav.addToGraph
import com.protonvpn.android.redesign.countries.ui.CountryListRoute
import com.protonvpn.android.redesign.countries.ui.CountryRoute
import com.protonvpn.android.redesign.countries.ui.ServersRoute
import com.protonvpn.android.redesign.main_screen.ui.nav.BottomSheetNav
import com.protonvpn.android.redesign.main_screen.ui.nav.MainNav
import kotlinx.serialization.Serializable

object CountryListScreen : ScreenNoArg<MainNav>("country_list") {

    fun SafeNavGraphBuilder<MainNav>.countryList(
        onCountryClick: (String) -> Unit
    ) = addToGraph(this) {
        CountryListRoute(onCountryClick)
    }
}

object CountryScreen : Screen<CountryScreen.Args, BottomSheetNav>("country") {

    @Serializable
    data class Args(val country: String)

    fun SafeNavGraphBuilder<BottomSheetNav>.country(
        onCityClicked: (String) -> Unit
    ) = addToGraph(this) { entry ->
        val (country) = getArgs<Args>(entry)
        CountryRoute(country, onCityClicked)
    }
}

object ServersScreen : Screen<ServersScreen.Args, BottomSheetNav>("servers") {

    @Serializable
    data class Args(val country: String, val city: String)

    fun SafeNavGraphBuilder<BottomSheetNav>.servers(
        onServerClicked: (String) -> Unit
    ) = addToGraph(this) { entry ->
        val (country, city) = getArgs<Args>(entry)
        ServersRoute(country, city, onServerClicked)
    }
}
