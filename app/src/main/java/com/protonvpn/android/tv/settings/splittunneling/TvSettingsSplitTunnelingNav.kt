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

package com.protonvpn.android.tv.settings.splittunneling

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.protonvpn.android.base.ui.nav.BaseNav
import com.protonvpn.android.base.ui.nav.Screen
import com.protonvpn.android.base.ui.nav.ScreenNoArg
import com.protonvpn.android.base.ui.nav.VpnNavHostController
import com.protonvpn.android.base.ui.nav.addToGraph
import com.protonvpn.android.settings.data.SplitTunnelingMode

class TvSplitTunnelingNav(
    selfNav: VpnNavHostController,
) : BaseNav<TvSplitTunnelingNav>(selfNav, "TvSettingsSplitTunnelingActivity") {

    @Composable
    fun NavHost(
        navigateBack: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        SafeNavHost(startScreen = MainScreen, modifier = modifier) {
            val navigateEditApps = { mode: SplitTunnelingMode -> navigateInternal(AppsScreen, mode) }
            MainScreen.addToGraph(this) {
                TvSettingsSplitTunnelingMainRoute(navigateEditApps, navigateBack)
            }
            AppsScreen.addToGraph(this) { entry ->
                TvSettingsSplitTunnelingAppsRoute(AppsScreen.getArgs<SplitTunnelingMode>(entry))
            }
        }
    }
}

object MainScreen : ScreenNoArg<TvSplitTunnelingNav>("main")
object AppsScreen : Screen<SplitTunnelingMode, TvSplitTunnelingNav>("apps")
