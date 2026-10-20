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

package com.protonvpn.android.tests.tv.main_screen

import androidx.compose.runtime.Composable
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.rememberDrawerState
import com.android.tools.screenshot.PreviewTest
import com.protonvpn.android.annotations.ProtonVpnTvTestPreview
import com.protonvpn.android.base.ui.ProtonVpnTvPreview
import com.protonvpn.android.base.ui.nav.rememberVpnNavHostController
import com.protonvpn.android.tv.main_screen.ui.TvMainScreen
import com.protonvpn.android.tv.main_screen.ui.nav.TvMainTarget
import com.protonvpn.android.tv.vpn.ui.VpnStatusTvViewState

@PreviewTest
@ProtonVpnTvTestPreview
@Composable
fun TvMainScreen_Home_Screenshot() {
    TvMainScreenTest(target = TvMainTarget.Home, drawerOpen = false)
}

@PreviewTest
@ProtonVpnTvTestPreview
@Composable
fun TvMainScreen_Settings_Screenshot() {
    TvMainScreenTest(target = TvMainTarget.Settings, drawerOpen = false)
}

@PreviewTest
@ProtonVpnTvTestPreview
@Composable
fun TvMainScreen_HomeWithExpandedDrawer_Screenshot() {
    TvMainScreenTest(target = TvMainTarget.Home, drawerOpen = true)
}

@Composable
private fun TvMainScreenTest(
    target: TvMainTarget,
    drawerOpen: Boolean
) {
    ProtonVpnTvPreview {
        val drawerState = rememberDrawerState(
            if (drawerOpen) DrawerValue.Open else DrawerValue.Closed
        )
        TvMainScreen(
            navController = rememberVpnNavHostController(),
            drawerState = drawerState,
            vpnStatus = VpnStatusTvViewState.Status.Unprotected,
            startScreen = target,
        )
    }
}
