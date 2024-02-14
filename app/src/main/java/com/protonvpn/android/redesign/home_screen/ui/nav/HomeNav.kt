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

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import com.protonvpn.android.redesign.base.ui.nav.SafeNavGraphBuilder
import com.protonvpn.android.redesign.base.ui.nav.ScreenNoArg
import com.protonvpn.android.redesign.base.ui.nav.addToGraph
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
    
    private const val TRANSITION_DURATION_MILLIS = 400

    fun SafeNavGraphBuilder<RootNav>.connectionStatus(
        onClosePanel: () -> Unit
    ) = addToGraph(
        this,
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Up,
                animationSpec = tween(TRANSITION_DURATION_MILLIS)
            )
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Down,
                animationSpec = tween(TRANSITION_DURATION_MILLIS)
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Up,
                animationSpec = tween(TRANSITION_DURATION_MILLIS)
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Down,
                animationSpec = tween(TRANSITION_DURATION_MILLIS)
            )
        }) {
        ConnectionDetailsRoute(onClosePanel = onClosePanel)
    }
}
