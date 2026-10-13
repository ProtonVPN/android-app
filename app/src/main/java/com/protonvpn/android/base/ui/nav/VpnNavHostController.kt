/*
 * Copyright (c) 2026 Proton AG
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

package com.protonvpn.android.base.ui.nav

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.Navigator
import androidx.navigation.compose.rememberNavController

/**
 * [NavHostController] together with additional navigation state used by [BaseNav].
 *
 * [BaseNav] instances are not required to be remembered and may be recreated on recomposition, therefore any state
 * they need is kept here, with the same lifetime as the [NavHostController].
 *
 * Can only be created with [rememberVpnNavHostController] (sealed with a private implementation, so it can't be
 * instantiated elsewhere).
 */
sealed class VpnNavHostController(
    val navController: NavHostController
) {
    // Top level navigation requested before NavHost has set the graph, e.g. a nested NavHost when the activity is
    // recreated with another screen on top and the nested NavHost hasn't been composed yet.
    internal var pendingTopLevelScreen by mutableStateOf<ScreenNoArg<*>?>(null)
}

private class VpnNavHostControllerImpl(navController: NavHostController) : VpnNavHostController(navController)

/**
 * Equivalent of [rememberNavController].
 */
@Composable
fun rememberVpnNavHostController(vararg navigators: Navigator<out NavDestination>): VpnNavHostController {
    val navController = rememberNavController(*navigators)
    return remember(navController) { VpnNavHostControllerImpl(navController) }
}

@VisibleForTesting
fun createVpnNavHostControllerForTest(navController: NavHostController): VpnNavHostController =
    VpnNavHostControllerImpl(navController)
