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

package com.protonvpn.robots.mobile

import androidx.test.espresso.matcher.ViewMatchers
import com.google.android.material.textfield.TextInputEditText
import com.protonvpn.interfaces.Robot
import com.protonvpn.test.shared.TestUser
import me.proton.core.auth.presentation.R
import me.proton.core.auth.test.robot.login.LoginRobot
import me.proton.test.fusion.Fusion.view

// This robot is meant to fill missing actions from core's LoginRobot.
// Before adding anything make sure it's not created in that class.
object LoginRobotVpn : Robot {

    private val viewPasswordButton
        get() = view
            .withId(R.id.text_input_end_icon).withVisibility(ViewMatchers.Visibility.VISIBLE)//.hasDescendant(view.withId(R.id.passwordInput))
    private val needHelpButton get() = view.withId(R.id.login_menu_help)
    private val signInButton get() = view.withId(R.id.sign_in)
    private val signUpButton get() = view.withId(R.id.sign_up)
    private val snackBarLabel get() = view.withId(R.id.snackbar_text)
    private val helpForgotUsernameButton get() = view.withId(R.id.helpOptionForgotUsername)
    private val helpOptionForgotPasswordButton get() = view.withId(R.id.helpOptionForgotPassword)
    private val helpOtherIssuesButton get() = view.withId(R.id.helpOptionOtherIssues)

    // TODO Refactor sign in method to not use Proton's test user.
    fun signIn(testUser: TestUser): LoginRobotVpn {
        LoginRobot.login(testUser.email, testUser.password, isLoginTwoStepEnabled = true)
        return this
    }

    fun viewPassword(): LoginRobotVpn {
        viewPasswordButton.click()
        return this
    }

    fun selectNeedHelp(): LoginRobotVpn {
        needHelpButton.click()
        return this
    }

    fun passwordIsVisible(testUser: TestUser) {
        view.instanceOf(TextInputEditText::class.java).withText(testUser.password)
            .await { checkIsDisplayed() }
    }

    fun needHelpOptionsAreDisplayed() {
        helpForgotUsernameButton.checkIsDisplayed()
        helpOptionForgotPasswordButton.checkIsDisplayed()
        helpOtherIssuesButton.checkIsDisplayed()
    }

    fun incorrectLoginCredentialsIsShown() {
        snackBarLabel.containsText("Incorrect login credentials. Please try again")
    }

    fun loginScreenIsDisplayed() {
        signInButton.checkIsDisplayed()
        signUpButton.checkIsDisplayed()
    }
}