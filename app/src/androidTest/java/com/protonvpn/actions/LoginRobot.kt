/*
 *  Copyright (c) 2021 Proton AG
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

package com.protonvpn.actions

import com.google.android.material.textfield.TextInputEditText
import com.protonvpn.base.BaseRobot
import com.protonvpn.base.BaseVerify
import com.protonvpn.test.shared.TestUser
import me.proton.core.test.quark.data.User
import java.net.URLDecoder
import me.proton.core.auth.presentation.R as AuthR

/**
 * [LoginRobot] Contains all actions and verifications for login screen
 */
class LoginRobot : BaseRobot() {

    fun signIn(testUser: TestUser) {
        enterCredentials(testUser)
        clickElementById<LoginRobot>(AuthR.id.signInButton)
    }

    fun signIn(testUser: User) {
        replaceText<LoginRobot>(AuthR.id.usernameInput, testUser.name)
        replaceText<LoginRobot>(AuthR.id.passwordInput, URLDecoder.decode(testUser.password, "utf-8"))
        clickElementById<LoginRobot>(AuthR.id.signInButton)
    }

    fun signInWithIncorrectCredentials(): LoginRobot {
        enterCredentials(TestUser.badUser)
        return clickElementById(AuthR.id.signInButton)
    }

    fun enterCredentials(testUser: TestUser): LoginRobot {
        replaceText<LoginRobot>(AuthR.id.usernameInput, testUser.email)
        return replaceText(AuthR.id.passwordInput, testUser.password)
    }

    fun viewPassword(): LoginRobot = clickElement(
        view.isDescendantOf(view.withId(AuthR.id.passwordInput)).withId(AuthR.id.text_input_end_icon)
    )

    fun selectNeedHelp(): LoginRobot = clickElementById(AuthR.id.login_menu_help)

    class Verify : BaseVerify() {

        fun passwordIsVisible(testUser: TestUser) {
            checkIfElementIsDisplayedByText(testUser.password, TextInputEditText::class.java)
        }

        fun userNameIsVisible(testUser: TestUser) = checkIfElementIsDisplayedByText(testUser.email)

        fun needHelpOptionsAreDisplayed() {
            checkIfElementIsDisplayedById(AuthR.id.helpOptionForgotUsername)
            checkIfElementIsDisplayedById(AuthR.id.helpOptionForgotPassword)
            checkIfElementIsDisplayedById(AuthR.id.helpOptionOtherIssues)
            checkIfElementIsDisplayedById(AuthR.id.helpOptionOtherIssues)
        }

        fun incorrectLoginCredentialsIsShown() {
            checkIfElementByIdContainsText(
                AuthR.id.snackbar_text,
                "Incorrect login credentials. Please try again"
            )
        }

        fun loginScreenIsDisplayed() {
            checkIfElementIsDisplayedById(AuthR.id.sign_in)
            checkIfElementIsDisplayedById(AuthR.id.sign_up)
        }
    }

    inline fun verify(block: Verify.() -> Unit) = Verify().apply(block)

}
