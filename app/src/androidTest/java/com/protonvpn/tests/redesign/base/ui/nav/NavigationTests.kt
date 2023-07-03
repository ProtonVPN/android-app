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

package com.protonvpn.tests.redesign.base.ui.nav

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import androidx.navigation.testing.TestNavHostController
import androidx.test.internal.runner.junit4.statement.UiThreadStatement.runOnUiThread
import com.google.accompanist.navigation.animation.AnimatedComposeNavigator
import com.protonvpn.android.base.ui.theme.VpnTheme
import com.protonvpn.android.redesign.base.ui.nav.BaseNav
import com.protonvpn.android.redesign.base.ui.nav.Graph
import com.protonvpn.android.redesign.base.ui.nav.SafeNavGraphBuilder
import com.protonvpn.android.redesign.base.ui.nav.Screen
import com.protonvpn.android.redesign.base.ui.nav.ScreenNoArg
import com.protonvpn.android.redesign.base.ui.nav.addNested
import com.protonvpn.android.redesign.base.ui.nav.addToGraph
import com.protonvpn.android.redesign.base.ui.nav.backStackRoutes
import com.protonvpn.android.redesign.base.ui.nav.popToStartNavOptions
import com.protonvpn.tests.redesign.base.ui.nav.ScreenA1.a1
import com.protonvpn.tests.redesign.base.ui.nav.ScreenA2.a2
import com.protonvpn.tests.redesign.base.ui.nav.ScreenA21.a21
import com.protonvpn.tests.redesign.base.ui.nav.ScreenA22.a22
import com.protonvpn.tests.redesign.base.ui.nav.ScreenA23.a23
import com.protonvpn.tests.redesign.base.ui.nav.ScreenA3.a3
import com.protonvpn.tests.redesign.base.ui.nav.ScreenB1.b1
import com.protonvpn.tests.redesign.base.ui.nav.ScreenB2.b2
import com.protonvpn.tests.redesign.base.ui.nav.ScreenB3.b3
import junit.framework.TestCase.assertEquals
import kotlinx.serialization.Serializable
import me.proton.core.compose.theme.ProtonTheme
import me.proton.test.fusion.Fusion.node
import me.proton.test.fusion.ui.compose.FusionComposeTest
import me.proton.test.fusion.ui.compose.builders.OnNode
import me.proton.test.fusion.ui.device.OnDevice
import org.junit.Before
import org.junit.Test

class NavA(navController: NavHostController) : BaseNav<NavA>(navController, "NavA")
class NavB(navController: NavHostController) : BaseNav<NavB>(navController, "NavB")

class NavigationTests : FusionComposeTest() {

    lateinit var navA: NavA
    lateinit var navB: NavB

    @OptIn(ExperimentalAnimationApi::class)
    @Before
    fun setup() {
        withContent {
            navA = NavA(TestNavHostController(LocalContext.current))
            navB = NavB(TestNavHostController(LocalContext.current))
            navA.controller.navigatorProvider.addNavigator(AnimatedComposeNavigator())
            navB.controller.navigatorProvider.addNavigator(AnimatedComposeNavigator())
            VpnTheme {
                Column(Modifier.fillMaxSize()) {
                    navA.SafeNavHost(
                        Modifier
                            .weight(1f)
                            .testTag(navA.name),
                        startScreen = ScreenA1
                    ) {
                        a1()
                        a2(start = ScreenA21) {
                            a21()
                            a22()
                            a23()
                        }
                        a3()
                    }
                    navB.SafeNavHost(
                        Modifier
                            .weight(1f)
                            .testTag(navB.name),
                        startScreen = ScreenB1
                    ) {
                        b1()
                        b2()
                        b3()
                    }
                }
            }
        }
    }

    @Test
    fun testPopBackStack() {
        verifyOn(navB, "B1", listOf("B1"))
        navB.navigate(ScreenB2, ScreenB3)
        runOnUiThread { navB.popBackStack() }
        verifyOn(navB, "B2", listOf("B1", "B2"))
    }

    @Test
    fun testPopUpToStartInNestedGraph() {
        // Entering A2 (nested graph) should automatically enter A21 (start location for it)
        navA.navigate(ScreenA2, ScreenA22, ScreenA21)
        verifyOn(navA, "A21", listOf("A1", "A2", "A21", "A22", "A21"))

        // We should go back to first A21 at the beginning for latest graph
        runOnUiThread { navA.popUpToStart() }
        verifyOn(navA, "A21", listOf("A1", "A2", "A21"))

        // Back should close nested graph
        OnDevice().pressBack()
        verifyOn(navA, "A1", listOf("A1"))
    }

    @Test
    fun testPopUpToStartInNestedGraphWhenSkippingDefaultScreen() {
        // Enter nested graph directly at second screen
        navA.navigate(ScreenA22, ScreenA21)
        verifyOn(navA, "A21", listOf("A1", "A2", "A22", "A21"))

        // popUpToStart should bring us to first screen after graph node (A2)
        runOnUiThread {
            navA.popUpToStart()
        }
        verifyOn(navA, "A22", listOf("A1", "A2", "A22"))
    }

    @Test
    fun testPopUpToStartNavOptions() {
        navB.navigate(ScreenB2, ScreenB3, navOptions = navB.controller.popToStartNavOptions())
        verifyOn(navB, "B3", listOf("B1", "B3"))

        // In nested graph
        navA.navigate(ScreenA2)
        verifyOn(navA, "A21", listOf("A1", "A2", "A21"))
        navA.navigate(ScreenA22, ScreenA23, navOptions = navA.controller.popToStartNavOptions())
        verifyOn(navA, "A23", listOf("A1", "A2", "A21", "A23"))
    }

    @Test
    fun testBackButtonOnBothGraphs() {
        navA.navigate(ScreenA3, ScreenA3.Args("s", 5))
        navB.navigate(ScreenB2, ScreenB3)

        // NavB navigation have preference so we NavA should be unchanged
        OnDevice().pressBack()
        OnDevice().pressBack()
        verifyOn(navA, "A3:s+5", listOf("A1", "A3"))
        verifyOn(navB, "B1", listOf("B1"))

        // Now that NavB is at start, NavA should navigate back
        OnDevice().pressBack()
        verifyOn(navB, "B1", listOf("B1"))
        verifyOn(navA, "A1", listOf("A1"))
    }
}

fun verifyOn(nav: BaseNav<*>, labelText: String, allRoutes: List<String>) {
    node.useUnmergedTree()
        .hasAncestor(OnNode().withTag(nav.name))
        .withText(labelText)
        .assertExists()
    assertEquals(allRoutes.last(), nav.currentRoute())
    assertEquals(allRoutes, nav.controller.backStackRoutes())
}

fun <N : BaseNav<N>> N.navigate(
    vararg screens: ScreenNoArg<N>,
    navOptions: NavOptions? = null
) = runOnUiThread {
    screens.forEach { navigateInternal(it, navOptions) }
}

inline fun <reified A : Any, N : BaseNav<N>> N.navigate(screen: Screen<A, N>, arg: A) =
    runOnUiThread { navigateInternal(screen, arg) }

fun <N : BaseNav<N>> Screen<*, N>.add(
    builder: SafeNavGraphBuilder<N>
) = addToGraph(builder) {
    Text(route, color = ProtonTheme.colors.textNorm)
}

object ScreenA1 : ScreenNoArg<NavA>("A1") {
    fun SafeNavGraphBuilder<NavA>.a1() = add(this)
}

object ScreenA2 : Graph<NavA>("A2") {
    fun SafeNavGraphBuilder<NavA>.a2(
        start: ScreenNoArg<NavA>,
        nestedGraph: SafeNavGraphBuilder<NavA>.() -> Unit
    ) = addNested(this, start, nestedGraph)
}

object ScreenA21 : ScreenNoArg<NavA>("A21") {
    fun SafeNavGraphBuilder<NavA>.a21() = add(this)
}

object ScreenA22 : ScreenNoArg<NavA>("A22") {
    fun SafeNavGraphBuilder<NavA>.a22() = add(this)
}

object ScreenA23 : ScreenNoArg<NavA>("A23") {
    fun SafeNavGraphBuilder<NavA>.a23() = add(this)
}

object ScreenA3 : Screen<ScreenA3.Args, NavA>("A3") {
    @Serializable
    data class Args(val s: String, val i: Int)

    fun SafeNavGraphBuilder<NavA>.a3() =
        addToGraph(this) {
            val (s, i) = getArgs<Args>(it)
            Text("$route:$s+$i", color = ProtonTheme.colors.textNorm)
        }
}

object ScreenB1 : ScreenNoArg<NavB>("B1") {
    fun SafeNavGraphBuilder<NavB>.b1() = add(this)
}

object ScreenB2 : ScreenNoArg<NavB>("B2") {
    fun SafeNavGraphBuilder<NavB>.b2() = add(this)
}

object ScreenB3 : ScreenNoArg<NavB>("B3") {
    fun SafeNavGraphBuilder<NavB>.b3() = add(this)
}
