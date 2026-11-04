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

package com.protonvpn.robots.tv

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.protonvpn.android.R
import com.protonvpn.base.BaseRobot
import com.protonvpn.data.Timeouts
import me.proton.test.fusion.Fusion.node
import kotlin.time.Duration.Companion.seconds

/**
 * [TvMainScreenRobot] Contains navigation between the sections of the main screen.
 *
 * In the redesigned UI the sections are destinations in the navigation drawer, in the legacy UI they are rows
 * on a single screen.
 */
class TvMainScreenRobot : BaseRobot() {

    private val uiDevice: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    /**
     * Opens settings, expects focus on the VPN status action in home.
     */
    fun openSettings(): TvSettingsRobot {
        if (isTvMainScreenRedesign()) {
            // The VPN status action button is focused on start, moving left from it focuses the drawer which then
            // expands and shows item labels.
            waitForVpnStatusAction()
            uiDevice.pressDPadLeft()
            node.withText(R.string.botton_nav_settings).performSemanticsAction(SemanticsActions.OnClick)
        } else {
            view.waitForCondition(watchTimeout = Timeouts.TWENTY_SECONDS_MS) {
                uiDevice.pressDPadDown()
                onView(withText(R.string.tv_row_settings)).check(matches(isDisplayed()))
            }
            uiDevice.pressDPadDown()
        }
        return TvSettingsRobot()
    }

    /**
     * Opens home, expects focus in settings.
     */
    fun openHome(): TvHomeRobot {
        if (isTvMainScreenRedesign()) {
            // Back moves focus to the drawer which then expands and shows item labels.
            uiDevice.pressBack()
            node.withText(R.string.bottom_nav_home).performSemanticsAction(SemanticsActions.OnClick)
            waitForVpnStatusAction()
        }
        return TvHomeRobot()
    }

    private fun waitForVpnStatusAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val connectFastest = context.getString(R.string.tv_vpn_status_action_connect_fastest)
        val disconnect = context.getString(R.string.disconnect)
        node.addSemanticMatcher(hasText(connectFastest) or hasText(disconnect))
            .await(20.seconds) { assertExists() }
    }
}
