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

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.protonvpn.android.profiles.ui.nav.AddEditProfileScreen
import com.protonvpn.android.profiles.ui.nav.ProfilesScreen
import com.protonvpn.android.profiles.ui.nav.ProfilesScreen.profiles
import com.protonvpn.android.redesign.app.ui.CoreNavigation
import com.protonvpn.android.redesign.app.ui.MainActivityViewModel
import com.protonvpn.android.redesign.app.ui.SettingsChangeViewModel
import com.protonvpn.android.redesign.app.ui.nav.RootNav
import com.protonvpn.android.redesign.base.ui.nav.BaseNav
import com.protonvpn.android.redesign.base.ui.nav.SafeNavGraphBuilder
import com.protonvpn.android.redesign.base.ui.nav.ScreenNoArg
import com.protonvpn.android.redesign.base.ui.nav.addToGraph
import com.protonvpn.android.redesign.base.ui.nav.baseRoute
import com.protonvpn.android.redesign.base.ui.nav.popToStartNavOptions
import com.protonvpn.android.redesign.countries.ui.nav.CountryListScreen
import com.protonvpn.android.redesign.countries.ui.nav.CountryListScreen.countryList
import com.protonvpn.android.redesign.countries.ui.nav.GatewaysScreen
import com.protonvpn.android.redesign.countries.ui.nav.GatewaysScreen.gateways
import com.protonvpn.android.redesign.countries.ui.nav.SearchRouteScreen
import com.protonvpn.android.redesign.home_screen.ui.ShowcaseRecents
import com.protonvpn.android.redesign.home_screen.ui.nav.ConnectionDetailsScreen
import com.protonvpn.android.redesign.home_screen.ui.nav.HomeScreen
import com.protonvpn.android.redesign.home_screen.ui.nav.HomeScreen.home
import com.protonvpn.android.redesign.main_screen.ui.BottomBarView
import com.protonvpn.android.redesign.main_screen.ui.MainScreenViewModel
import com.protonvpn.android.redesign.settings.ui.nav.SettingsScreen
import com.protonvpn.android.redesign.settings.ui.nav.SettingsScreen.settings
import com.protonvpn.android.redesign.settings.ui.nav.SubSettingsScreen
import java.util.EnumSet

enum class MainTarget {
    Home, Countries, Gateways, Profiles, Settings;

    companion object {
        fun fromRoute(baseRoute: String?) = when (baseRoute) {
            HomeScreen.route -> Home
            ProfilesScreen.route -> Profiles
            GatewaysScreen.route -> Gateways
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

            MainTarget.Profiles ->
                navigateInternal(ProfilesScreen, navOptions)

            MainTarget.Gateways ->
                navigateInternal(GatewaysScreen, navOptions)

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
        settingsChangeViewModel: SettingsChangeViewModel,
        modifier: Modifier,
    ) {
        val mainScreenViewModel = hiltViewModel<MainScreenViewModel>()
        SafeNavHost(
            modifier = modifier,
            startScreen = HomeScreen,
        ) {
            val onNavigateToHomeOnConnect = { showcaseRecents : ShowcaseRecents ->
                mainScreenViewModel.requestCollapseRecents(showcaseRecents)
                navigate(MainTarget.Home)
            }
            MainTarget.entries.forEach { target ->
                when (target) {
                    MainTarget.Home -> home(
                        mainScreenViewModel = mainScreenViewModel,
                        onConnectionCardClick = { rootNav.navigate(ConnectionDetailsScreen) }
                    )

                    MainTarget.Profiles ->
                        profiles(
                            onNavigateToHomeOnConnect = onNavigateToHomeOnConnect,
                            onNavigateToAddEdit = { profileId, duplicate ->
                                rootNav.navigate(
                                    AddEditProfileScreen,
                                    AddEditProfileScreen.ProfileCreationArgs(profileId, duplicate)
                                )
                            }
                        )

                    MainTarget.Gateways ->
                        gateways(onNavigateToHomeOnConnect = onNavigateToHomeOnConnect)

                    MainTarget.Countries -> countryList(
                        onNavigateToHomeOnConnect = onNavigateToHomeOnConnect,
                        onNavigateToSearch = { rootNav.navigate(SearchRouteScreen) }
                    )

                    MainTarget.Settings -> settings(
                        settingsChangeViewModel,
                        coreNavigation,
                        onNavigateToSubSetting = { type -> rootNav.navigate(SubSettingsScreen, type) },
                        onNavigateToEditProfile = { profileId, profileCreationTarget ->
                            rootNav.navigate(AddEditProfileScreen, AddEditProfileScreen.ProfileCreationArgs(profileId, navigateTo = profileCreationTarget))
                        }
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
        settingsChangeViewModel: SettingsChangeViewModel,
    ) {
        val bottomTarget = mainNav.currentBottomBarTargetAsState()
        val activity = LocalActivity.current as ComponentActivity
        val activityViewModel: MainActivityViewModel = hiltViewModel(viewModelStoreOwner = activity)
        val showGateways = activityViewModel.showGatewaysFlow.collectAsStateWithLifecycle().value ?: false
        val showProfiles = activityViewModel.showProfilesFlow.collectAsStateWithLifecycle().value ?: false
        val showProfilesDot by activityViewModel.autoShowInfoSheet.collectAsStateWithLifecycle(false)
        val notificationDots = when {
            showProfilesDot -> EnumSet.of(MainTarget.Profiles)
            else -> EnumSet.noneOf(MainTarget::class.java)
        }
        Scaffold(
            contentWindowInsets = WindowInsets.navigationBars,
            bottomBar = {
                BottomBarView(
                    selectedTarget = bottomTarget,
                    showGateways = showGateways,
                    showProfiles = showProfiles,
                    notificationDots = notificationDots,
                    navigateTo = mainNav::navigate
                )
            }
        ) { paddingValues ->
            mainNav.NavHost(
                settingsChangeViewModel,
                modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            )
        }
    }

    fun SafeNavGraphBuilder<RootNav>.mainScreen(
        mainNav: MainNav,
        settingsChangeViewModel: SettingsChangeViewModel,
    ) = addToGraph(this) {
        MainScreenNavigation(Modifier, mainNav, settingsChangeViewModel)
    }
}
