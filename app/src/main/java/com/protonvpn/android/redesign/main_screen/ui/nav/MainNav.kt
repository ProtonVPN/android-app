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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.protonvpn.android.redesign.base.ui.nav.BaseNav
import com.protonvpn.android.redesign.base.ui.nav.baseRoute
import com.protonvpn.android.redesign.base.ui.nav.popToStartNavOptions
import com.protonvpn.android.redesign.countries.ui.nav.CountryListScreen
import com.protonvpn.android.redesign.countries.ui.nav.CountryListScreen.countryList
import com.protonvpn.android.redesign.countries.ui.nav.CountryScreen
import com.protonvpn.android.redesign.home_screen.ui.nav.HomeScreen
import com.protonvpn.android.redesign.home_screen.ui.nav.HomeScreen.home
import com.protonvpn.android.redesign.main_screen.ui.CoreNavigation
import com.protonvpn.android.redesign.settings.ui.nav.SettingsScreen
import com.protonvpn.android.redesign.settings.ui.nav.SettingsScreen.settings

enum class MainTarget {
    Home, Countries, Settings;

    companion object {
        fun fromRoute(baseRoute: String?) = when (baseRoute) {
            HomeScreen.route -> Home
            CountryListScreen.route -> Countries
            SettingsScreen.route -> Settings
            else -> null
        }
    }
}

@Composable
fun rememberMainNav(
    coreNavigation: CoreNavigation,
    navController: NavHostController = rememberNavController(),
) = remember(navController) {
    MainNav(navController, coreNavigation)
}

class MainNav(
    mainNavController: NavHostController,
    private val coreNavigation: CoreNavigation,
) : BaseNav<MainNav>(mainNavController, "main") {

    fun navigate(target: MainTarget) {
        // Don't record whole history of bottom bar navigation
        val navOptions = controller.popToStartNavOptions()
        when (target) {
            MainTarget.Home ->
                navigateInternal(HomeScreen, navOptions)
            MainTarget.Countries ->
                navigateInternal(CountryListScreen, navOptions)
            MainTarget.Settings ->
                navigateInternal(SettingsScreen, navOptions)
        }
    }

    // Gets current bottom bar target and triggers recomposition on change
    @Composable
    fun currentBottomBarTargetAsState(): MainTarget? {
        val current = controller.currentBackStackEntryAsState()
        val currentRoute = current.value?.destination?.route?.baseRoute()
        return MainTarget.fromRoute(currentRoute)
    }

    @Composable
    fun NavGraph(
        modifier: Modifier,
        bottomSheetNav: BottomSheetNav,
    ) {
        SafeNavHost(
            modifier = modifier,
            startScreen = HomeScreen,
        ) {
            MainTarget.values().forEach { target ->
                when (target) {
                    MainTarget.Home -> home()
                    MainTarget.Countries -> countryList {
                        bottomSheetNav.navigate(CountryScreen, CountryScreen.Args(it))
                    }
                    MainTarget.Settings -> settings(coreNavigation)
                }
            }
        }
    }
}
