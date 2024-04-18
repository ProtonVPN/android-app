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

import com.protonvpn.android.redesign.app.ui.nav.RootNav
import com.protonvpn.android.redesign.base.ui.nav.SafeNavGraphBuilder
import com.protonvpn.android.redesign.base.ui.nav.ScreenNoArg
import com.protonvpn.android.redesign.base.ui.nav.addToGraph
import com.protonvpn.android.redesign.base.ui.nav.addToGraphWithSlideAnim
import com.protonvpn.android.redesign.countries.ui.CountriesRoute
import com.protonvpn.android.redesign.countries.ui.GatewaysRoute
import com.protonvpn.android.redesign.home_screen.ui.ShowcaseRecents
import com.protonvpn.android.redesign.main_screen.ui.nav.MainNav
import com.protonvpn.android.redesign.search.ui.SearchRoute

object CountryListScreen : ScreenNoArg<MainNav>("country_list") {

    fun SafeNavGraphBuilder<MainNav>.countryList(
        onNavigateToHomeOnConnect: (ShowcaseRecents) -> Unit,
        onNavigateToSearch: () -> Unit,
    ) = addToGraph(this) {
        CountriesRoute(onNavigateToHomeOnConnect, onNavigateToSearch)
    }
}

object GatewaysScreen : ScreenNoArg<MainNav>("gateways") {

    fun SafeNavGraphBuilder<MainNav>.gateways(
        onNavigateToHomeOnConnect: (ShowcaseRecents) -> Unit,
    ) = addToGraph(this) {
        GatewaysRoute(onNavigateToHomeOnConnect)
    }
}

object SearchRouteScreen : ScreenNoArg<RootNav>("searchScreen") {

    fun SafeNavGraphBuilder<RootNav>.searchScreen(
        onBackIconClick: () -> Unit,
        onNavigateToHomeOnConnect: (ShowcaseRecents) -> Unit,
    ) = addToGraphWithSlideAnim(this) {
        SearchRoute(onBackIconClick, onNavigateToHomeOnConnect)
    }
}
