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

package com.protonvpn.android.redesign.base.ui.nav

import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavOptions
import androidx.navigation.navOptions

// Nav options to rollback to start destination before navigating
fun NavController.popToStartNavOptions(): NavOptions =
    navOptions {
        val startId =
            currentDestination?.parent?.startDestinationId ?: graph.findStartDestination().id
        popUpTo(startId) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }

// Rollback to start destination
fun NavController.popUpToStart() {
    val startId = currentDestination?.parent?.startDestinationId ?: return
    while (!isAtTheStartOfGraph())
        popBackStack(startId, inclusive = currentDestination?.id == startId)
}

private fun NavController.isAtTheStartOfGraph(): Boolean {
    val previous = previousBackStackEntry ?: return true
    return previous.destination.parent != currentDestination?.parent
}

fun NavController.backStackRoutes(withArgs: Boolean = false): List<String> =
    currentBackStack.value
        .mapNotNull { it.destination.route }
        .run { if (withArgs) this else map { it.baseRoute() } }

fun NavController.currentRoute(): String? =
    currentBackStackEntry?.destination?.route?.baseRoute()

fun String.baseRoute() = split("?", "/")[0]
