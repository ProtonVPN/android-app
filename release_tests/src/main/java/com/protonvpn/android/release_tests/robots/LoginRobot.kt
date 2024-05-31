/*
 *
 *  * Copyright (c) 2023. Proton AG
 *  *
 *  * This file is part of ProtonVPN.
 *  *
 *  * ProtonVPN is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * ProtonVPN is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.protonvpn.android.release_tests.robots

import com.protonvpn.android.release_tests.data.TestConstants
import me.proton.test.fusion.Fusion.byObject
import me.proton.test.fusion.Fusion.device
import me.proton.test.fusion.ui.uiautomator.ByObject

object LoginRobot {
    fun signIn(username: String, password: String) {
        navigateToSignIn()
        enterCredentials(username, password)
        pressSignIn()
        waitUntilLoggedIn()
    }

    fun navigateToSignIn(): LoginRobot {
        byObject.withTimeout(TestConstants.TWENTY_SECOND_TIMEOUT).withText("Sign in").click()
        return this
    }

    fun enterCredentials(username: String, password: String): LoginRobot {
        protonInput("usernameInput").typeText(username)
        protonInput("passwordInput").typeText(password)
        return this
    }

    fun pressSignIn(): LoginRobot {
        // Use ID for the button because "Sign in" text is not unique (also used in header).
        byObject.withResId(TestConstants.TEST_PACKAGE, "signInButton").click()
        return this
    }

    fun waitUntilLoggedIn(): LoginRobot {
        byObject.withText("Connect").waitForExists(TestConstants.ONE_MINUTE_TIMEOUT)
        byObject.withText("You are unprotected").waitForExists()
        return this
    }

    private fun protonInput(resourceId: String): ByObject =
        byObject.withResId(TestConstants.TEST_PACKAGE, resourceId)
            .onDescendant(byObject.withResId(TestConstants.TEST_PACKAGE, "input"))
}