/*
 * Copyright (c) 2023-2026. Proton AG
 *
 *  This file is part of ProtonVPN.
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

package com.protonvpn.android.ui_automator_test_util.robots

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.protonvpn.android.ui_automator_test_util.data.TestConstants
import me.proton.test.fusion.Fusion.byObject
import me.proton.test.fusion.ui.uiautomator.ByObject

object LoginRobot {
    fun signIn(username: String, password: String): LoginRobot {
        navigateToSignIn()

        // Skip Fusion, it's nothing but limitations.
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val hasNewUsernameInput = uiDevice.wait(
            Until.hasObject(By.res("LOGIN_USERNAME_FIELD_TAG")),
            TestConstants.FIVE_SECONDS_TIMEOUT_MS
        )
        if (hasNewUsernameInput) {
            fillSignIn(username, password)
        } else {
            fillSignInLegacy(username, password)
        }

        return this
    }

    fun navigateToSignIn(): LoginRobot {
        byObject.withTimeout(TestConstants.TWENTY_SECOND_TIMEOUT).withText("Sign in").click()
        return this
    }

    fun waitUntilLoggedIn(): LoginRobot {
        byObject.withText("Connect").waitForExists(TestConstants.TWO_MINUTES_TIMEOUT)
        byObject.withText("You are unprotected").waitForExists()
        return this
    }

    private fun fillSignIn(username: String, password: String) {
        byObject.protonComposeInput("LOGIN_USERNAME_FIELD_TAG")
            .waitForExists(TestConstants.TWO_MINUTES_TIMEOUT).typeText(username)
        byObject.withText("Continue").click()
        byObject.protonComposeInput("LOGIN_PASSWORD_FIELD_TAG")
            .waitForExists(TestConstants.TWO_MINUTES_TIMEOUT).typeText(password)
        byObject.withText("Continue").click()
    }

    private fun fillSignInLegacy(username: String, password: String) {
        byObject.protonInput("usernameInput").typeText(username)
        byObject.protonInput("passwordInput").typeText(password)
        // Use ID for the button because "Sign in" text is not unique (also used in header).
        byObject.withResId(TestConstants.TEST_PACKAGE, "signInButton").click()
    }

    private fun ByObject.protonInput(resourceId: String): ByObject =
        withResId(TestConstants.TEST_PACKAGE, resourceId)
            .onDescendant(byObject.withResId(TestConstants.TEST_PACKAGE, "input"))

    private fun ByObject.protonComposeInput(testTag: String): ByObject =
        withResName(testTag)
            .onDescendant(byObject.withResName("PROTON_OUTLINED_TEXT_INPUT_TAG"))
}
