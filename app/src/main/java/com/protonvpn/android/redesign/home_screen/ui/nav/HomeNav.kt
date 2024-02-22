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

package com.protonvpn.android.redesign.home_screen.ui.nav

import com.protonvpn.android.redesign.base.ui.nav.SafeNavGraphBuilder
import com.protonvpn.android.redesign.base.ui.nav.ScreenNoArg
import com.protonvpn.android.redesign.base.ui.nav.addToGraph
import com.protonvpn.android.redesign.base.ui.nav.addToGraphWithSlideAnim
import com.protonvpn.android.redesign.home_screen.ui.ConnectionDetailsRoute
import com.protonvpn.android.redesign.home_screen.ui.HomeRoute
import com.protonvpn.android.redesign.main_screen.ui.MainScreenViewModel
import com.protonvpn.android.redesign.main_screen.ui.nav.MainNav
import com.protonvpn.android.redesign.app.ui.nav.RootNav

object HomeScreen : ScreenNoArg<MainNav>("home") {
    fun SafeNavGraphBuilder<MainNav>.home(
        mainScreenViewModel: MainScreenViewModel,
        onConnectionCardClick: () -> Unit
    ) = addToGraph(this) {
        HomeRoute(mainScreenViewModel, onConnectionCardClick)
    }
}

object ConnectionDetailsScreen : ScreenNoArg<RootNav>("connectionStatus") {

    fun SafeNavGraphBuilder<RootNav>.connectionStatus(
        onClosePanel: () -> Unit
    ) = addToGraphWithSlideAnim(this) {
        ConnectionDetailsRoute(onClosePanel = onClosePanel)
    }
}
