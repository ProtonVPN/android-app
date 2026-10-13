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

package com.protonvpn.app.base.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.protonvpn.android.base.ui.nav.BaseNav
import com.protonvpn.android.base.ui.nav.ScreenNoArg
import com.protonvpn.android.base.ui.nav.VpnNavHostController
import com.protonvpn.android.base.ui.nav.addToGraph
import com.protonvpn.android.base.ui.nav.rememberVpnNavHostController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private class OuterNav(controller: VpnNavHostController) : BaseNav<OuterNav>(controller, "outer") {
    fun navigate(screen: ScreenNoArg<OuterNav>) = navigateInternal(screen)
}

private class InnerNav(controller: VpnNavHostController) : BaseNav<InnerNav>(controller, "inner") {
    fun navigate(screen: ScreenNoArg<InnerNav>) = navigateToTopLevel(screen)
}

private object OuterMainScreen : ScreenNoArg<OuterNav>("main")
private object OuterSearchScreen : ScreenNoArg<OuterNav>("search")
private object InnerHomeTab : ScreenNoArg<InnerNav>("home")
private object InnerCountriesTab : ScreenNoArg<InnerNav>("countries")

// Mirrors RootNav (outer) and MainNav (inner): the inner NavHost is part of the outer OuterMainScreen, its controller is
// remembered outside of it. Nav objects are not remembered, like some of the app's navs.
@RunWith(RobolectricTestRunner::class)
class NestedNavRestorationTests {

    @get:Rule
    val composeRule = createComposeRule()

    private val restorationTester = StateRestorationTester(composeRule)

    private lateinit var outerNav: OuterNav
    private lateinit var innerNav: InnerNav
    private val recompositionTrigger = mutableIntStateOf(0)

    private fun setContent() {
        restorationTester.setContent {
            recompositionTrigger.intValue // Read to recompose on demand.
            val inner = InnerNav(rememberVpnNavHostController())
            innerNav = inner
            outerNav = OuterNav(rememberVpnNavHostController())
            outerNav.SafeNavHost(startScreen = OuterMainScreen) {
                OuterMainScreen.addToGraph(this) {
                    inner.SafeNavHost(startScreen = InnerHomeTab) {
                        listOf(InnerHomeTab, InnerCountriesTab).forEach { screen ->
                            screen.addToGraph(this) { Box(Modifier.fillMaxSize().testTag(screen.route)) }
                        }
                    }
                }
                OuterSearchScreen.addToGraph(this) { Box(Modifier.fillMaxSize().testTag(OuterSearchScreen.route)) }
            }
        }
    }

    private fun restoreWithSearchOnTop() {
        setContent()
        composeRule.runOnUiThread { innerNav.navigate(InnerCountriesTab) }
        composeRule.runOnUiThread { outerNav.navigate(OuterSearchScreen) }
        composeRule.waitForIdle()

        // Activity recreated with search on top: the nested NavHost is not composed, its graph is not set.
        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithTag(OuterSearchScreen.route).assertExists()
        assertNull(innerNav.controller.currentBackStackEntry)
    }

    private fun assertOnInnerHome() {
        assertEquals(OuterMainScreen.route, outerNav.currentRoute())
        assertEquals(InnerHomeTab.route, innerNav.currentRoute())
        composeRule.onNodeWithTag(InnerHomeTab.route).assertExists()
    }

    @Test
    fun `top level navigation in nested graph before it is restored is executed once it is composed`() {
        restoreWithSearchOnTop()

        // Same as connecting from search.
        composeRule.runOnUiThread {
            outerNav.navigateUp()
            innerNav.navigate(InnerHomeTab)
        }
        composeRule.waitForIdle()

        assertOnInnerHome()
    }

    @Test
    fun `deferred top level navigation is executed when nav object is recreated before NavHost is composed`() {
        restoreWithSearchOnTop()
        val navBeforeRecomposition = innerNav

        composeRule.runOnUiThread {
            outerNav.navigateUp()
            innerNav.navigate(InnerHomeTab)
            recompositionTrigger.intValue++
        }
        composeRule.waitForIdle()

        assertNotSame(navBeforeRecomposition, innerNav)
        assertOnInnerHome()
    }
}
