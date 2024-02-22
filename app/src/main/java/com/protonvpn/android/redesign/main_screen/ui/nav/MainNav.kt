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

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.protonvpn.android.redesign.base.ui.nav.BaseNav
import com.protonvpn.android.redesign.base.ui.nav.SafeNavGraphBuilder
import com.protonvpn.android.redesign.base.ui.nav.ScreenNoArg
import com.protonvpn.android.redesign.base.ui.nav.addToGraph
import com.protonvpn.android.redesign.base.ui.nav.baseRoute
import com.protonvpn.android.redesign.base.ui.nav.popToStartNavOptions
import com.protonvpn.android.redesign.countries.ui.nav.CountryListScreen
import com.protonvpn.android.redesign.countries.ui.nav.CountryListScreen.countryList
import com.protonvpn.android.redesign.countries.ui.nav.SearchRouteScreen
import com.protonvpn.android.redesign.home_screen.ui.nav.ConnectionDetailsScreen
import com.protonvpn.android.redesign.home_screen.ui.nav.HomeScreen
import com.protonvpn.android.redesign.home_screen.ui.nav.HomeScreen.home
import com.protonvpn.android.redesign.main_screen.ui.BottomBarView
import com.protonvpn.android.redesign.app.ui.CoreNavigation
import com.protonvpn.android.redesign.app.ui.nav.RootNav
import com.protonvpn.android.redesign.main_screen.ui.MainScreenViewModel
import com.protonvpn.android.redesign.settings.ui.nav.SettingsScreen
import com.protonvpn.android.redesign.settings.ui.nav.SettingsScreen.settings
import com.protonvpn.android.redesign.settings.ui.nav.SubSettingsScreen

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
    rootNav: RootNav,
    selfController: NavHostController = rememberNavController(),
) = remember(selfController) {
    MainNav(
        selfNav = selfController,
        coreNavigation = coreNavigation,
        rootNav = rootNav
    )
}

class MainNav(
    selfNav: NavHostController,
    private val coreNavigation: CoreNavigation,
    private val rootNav: RootNav,
) : BaseNav<MainNav>(selfNav, "main") {

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
    fun NavHost(
        modifier: Modifier,
    ) {
        val mainScreenViewModel = hiltViewModel<MainScreenViewModel>()
        SafeNavHost(
            modifier = modifier,
            startScreen = HomeScreen,
        ) {
            MainTarget.values().forEach { target ->
                when (target) {
                    MainTarget.Home -> home(
                        mainScreenViewModel = mainScreenViewModel,
                        onConnectionCardClick = { rootNav.navigate(ConnectionDetailsScreen) }
                    )

                    MainTarget.Countries -> countryList(
                        onNavigateToHomeOnConnect = { showcaseRecents ->
                            mainScreenViewModel.requestCollapseRecents(showcaseRecents)
                            navigate(MainTarget.Home)
                        },
                        onNavigateToSearch = {
                            rootNav.navigate(SearchRouteScreen)
                        }
                    )

                    MainTarget.Settings -> settings(
                        coreNavigation,
                        onNavigateToSubSetting = { type -> rootNav.navigate(SubSettingsScreen, type) }
                    )
                }
            }
        }
    }
}


object MainScreen : ScreenNoArg<RootNav>("main") {
    @Composable
    private fun MainScreenNavigation(
        modifier: Modifier,
        mainNav: MainNav,
    ) {
        val bottomTarget = mainNav.currentBottomBarTargetAsState()
        Scaffold(
            contentWindowInsets = WindowInsets.navigationBars,
            bottomBar = {
                BottomBarView(selectedTarget = bottomTarget, navigateTo = mainNav::navigate)
            }
        ) { paddingValues ->
            mainNav.NavHost(
                modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            )
        }
    }

    fun SafeNavGraphBuilder<RootNav>.mainScreen(
        coreNavigation: CoreNavigation,
        rootNav: RootNav
    ) = addToGraph(this) {
        val mainNav = rememberMainNav(coreNavigation, rootNav)
        MainScreenNavigation(Modifier, mainNav)
    }
}
