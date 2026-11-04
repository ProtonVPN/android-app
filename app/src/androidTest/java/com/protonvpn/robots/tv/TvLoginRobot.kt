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

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.protonvpn.android.R
import com.protonvpn.data.Timeouts
import me.proton.test.fusion.Fusion.device
import me.proton.test.fusion.Fusion.node
import me.proton.test.fusion.FusionConfig
import me.proton.test.fusion.data.Robot

class TvLoginRobot : Robot {

    private val textSignInMessage get() = node.withText(R.string.session_fork_qr_code_message)
    private val textErrorNetworkMessage get() =
        node.withText(R.string.session_fork_qr_code_error_network)
    private val buttonCreateNewCode get() =
        node.withText(R.string.session_fork_qr_code_error_create_new_button)
    private val textCode get() =
        node.withTextSubstring("Enter the code:")

    fun navigateToTroubleSigningIn() {
        // There are issues with focus in compose, pressing down twice seems to help.
        device.pressKeyCode(KeyEvent.KEYCODE_DPAD_DOWN)
        device.pressKeyCode(KeyEvent.KEYCODE_DPAD_DOWN)
        device.pressEnter()
    }

    fun getUserCode(): String {
        waitFor {
            textCode.assertIsDisplayed()
        }
        val text = textCode.interaction.fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .first { it.startsWith("Enter the code") }
        return text.takeLast(9).filterNot { it.isWhitespace() }.toString()
    }

    fun waitForQrCode() = waitFor {
        textSignInMessage.assertIsDisplayed()
    }

    fun assertErrorNetwork() {
        textErrorNetworkMessage.assertIsDisplayed()
    }

    fun clickCreateNewCode() {
        buttonCreateNewCode.assertIsDisplayed()
        device.pressKeyCode(KeyEvent.KEYCODE_ENTER)
    }

    fun waitUntilLoggedIn(): TvCountryListRobot {
        // The TvMainScreenRedesign flag is user-scoped and can't be read before the login completes, wait for
        // either variant of the main screen.
        // Use compose test rule's waitUntil, it advances the compose clock needed for TvQrLoginActivity to
        // navigate to the main screen.
        val composeRule = FusionConfig.Compose.testRule.get()
        val connectFastest = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.tv_vpn_status_action_connect_fastest)
        composeRule.waitUntil(Timeouts.ONE_MINUTE_MS) {
            composeRule.onAllNodesWithText(connectFastest).fetchSemanticsNodes().isNotEmpty() ||
                runCatching { onView(withText(R.string.quickConnect)).check(matches(isDisplayed())) }.isSuccess
        }
        return TvCountryListRobot()
    }

    override fun robotDisplayed() {
        waitForQrCode()
    }

    class Verify(val robot: TvLoginRobot) {
        fun signInMessageIsDisplayed() = robot.textSignInMessage.assertIsDisplayed()
    }

    inline fun verify(block: Verify.() -> Unit) = Verify(this).apply(block)
}
