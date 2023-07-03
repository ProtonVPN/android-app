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

import androidx.annotation.VisibleForTesting
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import com.google.accompanist.navigation.animation.AnimatedNavHost
import com.protonvpn.android.BuildConfig
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.LogEventType
import com.protonvpn.android.logging.LogLevel
import com.protonvpn.android.logging.ProtonLogger

val NavLog = LogEventType(LogCategory.UI, "NAV", LogLevel.INFO)

/**
 * Wrapper around [NavGraphBuilder] tying it to [N]: [BaseNav] preventing adding [Screen] that is
 * bound to another [BaseNav] child class. See [BaseNav] for more info.
 */
data class SafeNavGraphBuilder<N : BaseNav<N>>(val builder: NavGraphBuilder)

/**
 * Wrapper around [NavHostController] with more high-level interface, logging and navigation
 * type-safety. Dedicated [BaseNav] child classes should be used for different nav graphs and each
 * [Screen] will be tied to a single [BaseNav] child.
 *
 * @param N type of [BaseNav] child class for type-safety of graph construction and navigation. Only
 *  [Screen<*, T>] will can be added to this graph and navigated-to.
 *
 * Usage:
 * ```
 * class Nav1: BaseNav<Nav1>(...)
 * class Nav2: BaseNav<Nav2>(...)
 *
 * // Defining screen
 * object MyScreen: Screen<MyScreen.Args, Nav1>("my_screen") {
 *     @Serializable data class Args(...)
 *     fun SafeNavGraphBuilder<Nav1>.myScreen() = addToGraph(this) {
 *         MyScreenComposable()
 *     }
 * }
 *
 * // Defining nav graph (preferably as a part of graph-defining method in NavX child class)
 * nav1.SafeNavHost(startScreen = MyScreen) {
 *     myScreen() // This would fail at compile time if used with nav2
 *     myOtherScreen { ... }
 * }
 *
 * // Navigating to screen
 * fun navigate(...) { // high-level navigation method in NavX child class
 *     navigateInternal(MyScreen, MyScreen.Args(...)) // This would fail at compile time if used with nav2
 * }
 * ```
 */
open class BaseNav<N : BaseNav<N>>(
    val controller: NavHostController,
    val name: String,
) {
    init {
        printBackStackRoutes("init")
    }

    fun popUpToStart() {
        controller.popUpToStart()
        printBackStackRoutes("popUpToStart")
    }

    fun popBackStack() {
        controller.popBackStack()
        printBackStackRoutes("popBackStack")
    }

    fun navigateInternal(screen: ScreenNoArg<N>, navOptions: NavOptions? = null) {
        navLog("navigating to ${screen.route}")
        controller.navigate(screen.route, navOptions)
        printBackStackRoutes("navigate")
    }

    inline fun <reified A : Any> navigateInternal(
        screen: Screen<A, N>,
        arg: A,
        navOptions: NavOptions? = null
    ) {
        navLog("navigating to ${screen.route}")
        controller.navigate(screen.routeWith(arg), navOptions)
        printBackStackRoutes("navigate")
    }

    fun currentRoute() = controller.currentRoute()

    // Debug util to print navigator back stack
    fun printBackStackRoutes(prefix: String, withArgs: Boolean = false) {
        if (BuildConfig.DEBUG)
            navLog("$prefix: ${controller.backStackRoutes(withArgs)}")
    }

    fun navLog(message: String) {
        ProtonLogger.log(NavLog, "$name $message")
    }

    @OptIn(ExperimentalAnimationApi::class)
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    @Composable
    fun SafeNavHost(
        modifier: Modifier = Modifier,
        startScreen: ScreenNoArg<N>, // Starting screen can't have arguments
        build: SafeNavGraphBuilder<N>.() -> Unit
    ) {
        AnimatedNavHost(
            modifier = modifier,
            navController = controller,
            startDestination = startScreen.route,
        ) {
            SafeNavGraphBuilder<N>(this).build()
        }
    }
}
