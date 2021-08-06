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

class LogoutResult(val user: TestUser? = null) : BaseRobot() {
    @JvmField val connectionResult: ConnectionResult = ConnectionResult()

    //checks if login form appeared
    val isSuccessful: LogoutResult
        get() {
            //checks if login form appeared
            checkInputDisplayed(R.id.inputEmail)
            checkInputDisplayed(R.id.inputPassword)
            view.withId(R.id.buttonLogin).checkDisplayed()
            return LogoutResult()
        }

    // Check if login form is covered by error view.
    val isFailure: LogoutResult
        get() {
            //checks if login form has not appeared
            checkInputNotDisplayed(R.id.inputEmail)
            checkInputNotDisplayed(R.id.inputPassword)
            view.withId(R.id.buttonLogin).checkNotDisplayed()
            return LogoutResult()
        }

    // Check that login screen isn't shown.
    fun noLoginScreen(): LogoutResult {
        view.withId(R.id.inputEmail).checkDoesNotExist()
        view.withId(R.id.inputPassword).checkDoesNotExist()
        view.withId(R.id.buttonLogin).checkDoesNotExist()
        return LogoutResult()
    }

    fun warningMessageIsDisplayed(): HomeRobot {
        //check if warning message is displayed
        view
            .withId(R.id.md_title)
            .withText(R.string.warning)
            .checkDisplayed()
        view
            .withId(R.id.md_content)
            .withText("Logging out will disconnect your device from the VPN server.")
            .checkDisplayed()
        return HomeRobot()
    }

    fun userNameVisible(): LogoutResult {
        view
            .instanceOf(EditText::class.java)
            .withId(R.id.inputEmail)
            .withText(user!!.email)
            .checkDisplayed()
        return this
    }
}
