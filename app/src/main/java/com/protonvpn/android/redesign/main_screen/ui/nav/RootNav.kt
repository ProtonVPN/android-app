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

package com.protonvpn.android.redesign.main_screen.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.protonvpn.android.redesign.base.ui.nav.BaseNav
import com.protonvpn.android.redesign.countries.ui.nav.SearchRouteScreen.searchScreen
import com.protonvpn.android.redesign.home_screen.ui.nav.ConnectionDetailsScreen.connectionStatus
import com.protonvpn.android.redesign.main_screen.ui.CoreNavigation
import com.protonvpn.android.redesign.main_screen.ui.nav.MainScreen.mainScreen

enum class RootTarget {
    Main, ConnectionDetails, SearchScreen;
}

class RootNav(
    private val selfNav: NavHostController,
) : BaseNav<RootNav>(selfNav, "rootNav") {

    @Composable
    fun NavHost(
        modifier: Modifier,
        coreNavigation: CoreNavigation
    ) {
        SafeNavHost(
            modifier = modifier,
            startScreen = MainScreen,
        ) {
            RootTarget.values().forEach { target ->
                when (target) {
                    RootTarget.Main -> {
                        mainScreen(
                            coreNavigation,
                            selfNav
                        )
                    }
                    RootTarget.SearchScreen -> {
                        searchScreen(onBackIconClick = ::popBackStack)
                    }
                    RootTarget.ConnectionDetails -> {
                        connectionStatus(onClosePanel = ::popBackStack)
                    }
                }
            }
        }
    }
}
