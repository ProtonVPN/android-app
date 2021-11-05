/*
 * Copyright (c) 2018 Proton Technologies AG
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
package com.protonvpn.results

import android.widget.EditText
import com.protonvpn.actions.HomeRobot
import com.protonvpn.android.R
import com.protonvpn.base.BaseRobot
import com.protonvpn.test.shared.TestUser
import me.proton.core.test.android.robots.auth.login.LoginRobot

class LogoutResult(val user: TestUser? = null) : BaseRobot() {
    @JvmField val connectionResult: ConnectionResult = ConnectionResult()

    //checks if sign-in/up screen appeared
    val isSuccessful: LogoutResult
        get() {
            //checks if login screen appeared
            waitUntilDisplayed<LoginRobot>(R.id.sign_in)
            waitUntilDisplayed<LoginRobot>(R.id.sign_up)
            return LogoutResult()
        }

    // Check if login form is covered by error view.
    val isFailure: LogoutResult
        get() {
            //checks if login form has not appeared
            checkInputNotDisplayed(R.id.sign_in)
            checkInputNotDisplayed(R.id.sign_up)
            return LogoutResult()
        }

    // Check that login screen isn't shown.
    fun noLoginScreen(): LogoutResult {
        view.withId(R.id.sign_in).checkDoesNotExist()
        view.withId(R.id.sign_up).checkDoesNotExist()
        return LogoutResult()
    }

    fun warningMessageIsDisplayed(): HomeRobot {
        //check if warning message is displayed
        view
            .withText(R.string.logoutConfirmDialogTitle)
            .checkDisplayed()
        view
            .withText(R.string.logoutConfirmDialogMessage)
            .checkDisplayed()
        return HomeRobot()
    }

    fun userNameVisible(): LogoutResult {
        view
            .instanceOf(EditText::class.java)
            .withId(R.id.usernameInput)
            .withText(user!!.email)
            .checkDisplayed()
        return this
    }
}
