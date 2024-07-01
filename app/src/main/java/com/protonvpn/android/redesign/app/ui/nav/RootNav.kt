/*
 * Copyright (c) 2024. Proton AG
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

package com.protonvpn.android.redesign.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.protonvpn.android.redesign.base.ui.nav.BaseNav
import com.protonvpn.android.redesign.base.ui.nav.Screen
import com.protonvpn.android.redesign.base.ui.nav.ScreenNoArg
import com.protonvpn.android.redesign.countries.ui.nav.SearchRouteScreen.searchScreen
import com.protonvpn.android.redesign.home_screen.ui.nav.ConnectionDetailsScreen.connectionStatus
import com.protonvpn.android.redesign.app.ui.CoreNavigation
import com.protonvpn.android.redesign.app.ui.SettingsChangeViewModel
import com.protonvpn.android.redesign.main_screen.ui.nav.MainScreen
import com.protonvpn.android.redesign.main_screen.ui.nav.MainScreen.mainScreen
import com.protonvpn.android.redesign.main_screen.ui.nav.MainTarget
import com.protonvpn.android.redesign.main_screen.ui.nav.rememberMainNav
import com.protonvpn.android.redesign.settings.ui.nav.SubSettingsScreen
import com.protonvpn.android.redesign.settings.ui.nav.SubSettingsScreen.subSettings

enum class RootTarget {
    Main, ConnectionDetails, SearchScreen, SubSettings;
}

class RootNav(
    selfNav: NavHostController,
) : BaseNav<RootNav>(selfNav, "rootNav") {

    @Composable
    fun NavHost(
        settingsChangeViewModel: SettingsChangeViewModel,
        modifier: Modifier,
        coreNavigation: CoreNavigation
    ) {
        val mainNav = rememberMainNav(coreNavigation, this)
        SafeNavHost(
            modifier = modifier,
            startScreen = MainScreen,
        ) {
            RootTarget.entries.forEach { target ->
                when (target) {
                    RootTarget.Main -> {
                        mainScreen(mainNav, settingsChangeViewModel)
                    }
                    RootTarget.SearchScreen -> {
                        searchScreen(
                            onBackIconClick = ::popBackStack,
                            onNavigateToHomeOnConnect = {
                                popBackStack()
                                mainNav.navigate(MainTarget.Home)
                            }
                        )
                    }
                    RootTarget.ConnectionDetails -> {
                        connectionStatus(onClosePanel = ::popBackStack)
                    }
                    RootTarget.SubSettings -> {
                        subSettings(
                            settingsChangeViewModel = settingsChangeViewModel,
                            onClose = ::popBackStack,
                            onNavigateToSubSetting = { navigate(SubSettingsScreen, it) },
                        )
                    }
                }
            }
        }
    }

    inline fun <reified T: Any> navigate(screen: Screen<T, RootNav>, arg: T) {
        navigateInternal(screen, arg)
    }

    fun navigate(screen: ScreenNoArg<RootNav>) {
        navigateInternal(screen)
    }
}
