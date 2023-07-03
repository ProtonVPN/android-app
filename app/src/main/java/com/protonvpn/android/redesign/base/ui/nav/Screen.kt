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

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.Composable
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import com.google.accompanist.navigation.animation.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import me.proton.core.util.kotlin.deserialize
import me.proton.core.util.kotlin.serialize

/**
 * Holds navigation info for a given Compose Navigation destination in a more
 * type-safe manner. See [BaseNav] for more info.

 * @param A type of the navigation argument for the screen.
 * @param N type of the [BaseNav] that this screen is associated with.
 * @param route base route of the screen. It should be unique per [N].
 */
@Suppress("UnnecessaryAbstractClass")
abstract class Screen<A : Any, N : BaseNav<N>>(val route: String) {

    open val routePattern: String get() =
        "$route/{$ARG_NAME}"

    open val navArgs get() = listOf(
        navArgument(ARG_NAME) { type = NavType.StringType }
    )

    inline fun <reified T : A> routeWith(arg: T) =
        if (arg is Unit)
            route
        else
            routePattern.replace("{$ARG_NAME}", Uri.encode(arg.serialize()))

    inline fun <reified T : A> getArgs(entry: NavBackStackEntry): A =
        requireNotNull(entry.arguments?.getString(ARG_NAME)?.deserialize<T>())

    companion object {
        const val ARG_NAME = "arg"
    }
}

// Less-boilerplate [Screen] when no argument is needed.
abstract class ScreenNoArg<N : BaseNav<N>>(route: String) : Screen<Unit, N>(route) {
    override val routePattern: String get() = route
    override val navArgs get() = emptyList<NamedNavArgument>()
}

/**
 * No-content screen that acts as a parent for nested navigation.
 * Usage:
 * ```
 * object MyGraph: Graph<MyNav>("my-graph") {
 *    fun SafeNavGraphBuilder<MyNav>.a2() =
 *        addNested(this, startScreen = MyNestedScreen1) {
 *            myNestedScreen1()
 *            myNestedScreen2()
 *        }
 * }
 *
 * myNav.navigate(MyGraph) // Will navigate to MyNestedScreen1
 * myNav.navigate(MyNestedScreen2) // When navigated from outside of the graph MyNestedScreen2 will
 *    // act as a start screen
 * myNav.popUpBackToStart() // Will pop up back to start screen of this nested graph.
 * ```
 */
@Suppress("UnnecessaryAbstractClass")
abstract class Graph<N : BaseNav<N>>(route: String) : ScreenNoArg<N>(route)

// Add screen into the navigation graph.
// As extension method to workaround internal compiler errors.
@OptIn(ExperimentalAnimationApi::class)
fun <N : BaseNav<N>> Screen<*, N>.addToGraph(
    builder: SafeNavGraphBuilder<N>,
    enterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = null,
    exitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = null,
    popEnterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = null,
    popExitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = null,
    content: @Composable AnimatedVisibilityScope.(NavBackStackEntry) -> Unit
) {
    builder.builder.composable(
        routePattern,
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition,
        arguments = navArgs,
        content = content
    )
}

// Helper to build nested navigation graph.
fun <N : BaseNav<N>> Graph<N>.addNested(
    builder: SafeNavGraphBuilder<N>,
    startScreen: ScreenNoArg<N>,
    nestedGraph: SafeNavGraphBuilder<N>.() -> Unit,
) {
    builder.builder.navigation(
        route = routePattern,
        startDestination = startScreen.routePattern
    ) {
        SafeNavGraphBuilder<N>(this).nestedGraph()
    }
}
