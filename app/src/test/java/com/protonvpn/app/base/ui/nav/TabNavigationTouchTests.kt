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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.protonvpn.android.base.ui.nav.BaseNav
import com.protonvpn.android.base.ui.nav.SafeNavGraphBuilder
import com.protonvpn.android.base.ui.nav.ScreenNoArg
import com.protonvpn.android.base.ui.nav.addToGraph
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private class TabNav(controller: NavHostController) : BaseNav<TabNav>(controller, "tabs") {
    fun navigate(screen: ScreenNoArg<TabNav>) = navigateToTopLevel(screen)
}

private object HomeTab : ScreenNoArg<TabNav>("home")
private object SettingsTab : ScreenNoArg<TabNav>("settings")
private object ProfilesTab : ScreenNoArg<TabNav>("profiles")

@RunWith(RobolectricTestRunner::class)
class TabNavigationTouchTests {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var nav: TabNav
    private val clicks = mutableListOf<String>()

    private fun setContent() {
        composeRule.setContent {
            nav = TabNav(rememberNavController())
            nav.SafeNavHost(startScreen = HomeTab) {
                listOf(HomeTab, SettingsTab, ProfilesTab).forEach { screen ->
                    screen.addToGraph(this) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .testTag(screen.route)
                                .clickable { clicks += screen.route }
                        )
                    }
                }
            }
        }
        composeRule.mainClock.autoAdvance = false
    }

    private fun navigateAndTapQuickly(from: ScreenNoArg<TabNav>, to: ScreenNoArg<TabNav>) {
        composeRule.runOnUiThread { nav.navigate(from) }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.runOnUiThread { nav.navigate(to) }
        composeRule.mainClock.advanceTimeBy(100) // Transition in progress.
        // The transition is still running: the exiting screen is still there, it fades out.
        composeRule.onNodeWithTag(from.route).assertExists()
        composeRule.onNodeWithTag(to.route).performClick()
    }

    @Test
    fun `tapping right after switching to home is received by home`() {
        setContent()
        navigateAndTapQuickly(SettingsTab, HomeTab)
        assertEquals(listOf("home"), clicks)
    }

    @Test
    fun `tapping right after switching to other tab is received by that tab`() {
        setContent()
        navigateAndTapQuickly(SettingsTab, ProfilesTab)
        assertEquals(listOf("profiles"), clicks)
    }

    @Test
    fun `tapping right after switching to home from second tab is received by home`() {
        setContent()
        navigateAndTapQuickly(ProfilesTab, HomeTab)
        assertEquals(listOf("home"), clicks)
    }
}
