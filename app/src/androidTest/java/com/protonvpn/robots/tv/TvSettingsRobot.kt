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
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isSelected
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.protonvpn.android.R
import com.protonvpn.base.BaseRobot
import com.protonvpn.base.BaseVerify
import com.protonvpn.data.Timeouts
import me.proton.test.fusion.Fusion.node

/**
 * [TvSettingsRobot] Contains all actions and verifications for settings, including the sign out dialog.
 */
class TvSettingsRobot : BaseRobot() {

    private val uiDevice: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    fun signOut(): TvSettingsRobot {
        if (isTvMainScreenRedesign()) {
            node.withText(R.string.tv_signout_label)
                .scrollTo()
                .performSemanticsAction(SemanticsActions.OnClick)
        } else {
            // Using Compose test rule in tests using this robot makes these checks slow (~5s per loop),
            // to be investigated.
            view.waitForCondition(watchTimeout = Timeouts.ONE_MINUTE_MS) {
                uiDevice.pressDPadRight()
                onView(withText(R.string.tv_signout_label)).check(matches(isSelected()))
            }
            uiDevice.pressDPadCenter()
        }
        return this
    }

    fun confirmSignOut(): TvLoginRobot =
        if (isTvMainScreenRedesign()) {
            clickDialogNode(R.string.tv_signout_dialog_ok)
            TvLoginRobot()
        } else {
            clickDialogElementByText(R.string.dialog_sign_out_action)
        }

    fun cancelSignOut(): TvSettingsRobot =
        if (isTvMainScreenRedesign()) {
            clickDialogNode(R.string.cancel)
            this
        } else {
            clickDialogElementByText(R.string.cancel)
        }

    private fun clickDialogNode(textRes: Int) {
        node.withText(textRes).hasAncestor(node.isDialog()).performSemanticsAction(SemanticsActions.OnClick)
    }

    class Verify : BaseVerify() {
        fun signOutWhileConnectedWarningMessageIsDisplayed() {
            if (isTvMainScreenRedesign()) {
                node.withText(R.string.tv_signout_dialog_description_connected).await { assertIsDisplayed() }
            } else {
                checkIfDialogContainsText(R.string.tv_signout_dialog_description_connected)
            }
        }
    }

    inline fun verify(block: Verify.() -> Unit) = Verify().apply(block)
}
