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

package com.protonvpn.android.tv.main_screen.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import com.protonvpn.android.base.ui.nav.BaseNav
import com.protonvpn.android.base.ui.nav.ScreenNoArg
import com.protonvpn.android.base.ui.nav.VpnNavHostController
import com.protonvpn.android.base.ui.nav.addToGraph
import com.protonvpn.android.base.ui.nav.baseRoute
import com.protonvpn.android.base.ui.nav.popToStartNavOptions
import com.protonvpn.android.tv.home.TvHomeRoute
import com.protonvpn.android.tv.main_screen.ui.TvSettingsPlaceholder

enum class TvMainTarget(val screen: ScreenNoArg<TvMainScreenNav>) {
    Home(TvHomeScreen),
    Settings(TvSettingsScreen);

    companion object {
        fun fromRoute(baseRoute: String?) = entries.find { it.screen.route == baseRoute }
    }
}

// Navigation between the sections selected in the drawer.
class TvMainScreenNav(
    selfNav: VpnNavHostController,
    private val startScreen: TvMainTarget = TvMainTarget.Home,
) : BaseNav<TvMainScreenNav>(selfNav, "TvMainScreenNav") {

    fun navigate(target: TvMainTarget) {
        navigateInternal(target.screen, controller.popToStartNavOptions())
    }

    @Composable
    fun currentTargetAsState(): TvMainTarget? {
        val current by controller.currentBackStackEntryAsState()
        return TvMainTarget.fromRoute(current?.destination?.route?.baseRoute())
    }

    @Composable
    fun NavHost(modifier: Modifier = Modifier) {
        SafeNavHost(startScreen = startScreen.screen, modifier = modifier) {
            TvHomeScreen.addToGraph(this) { TvHomeRoute() }
            TvSettingsScreen.addToGraph(this) { TvSettingsPlaceholder() }
        }
    }
}

object TvHomeScreen : ScreenNoArg<TvMainScreenNav>("home")
object TvSettingsScreen : ScreenNoArg<TvMainScreenNav>("settings")
