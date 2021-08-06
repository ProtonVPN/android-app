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
import com.protonvpn.android.R
import com.protonvpn.base.BaseRobot
import com.protonvpn.test.shared.TestUser

class LoginFormResult(private var user: TestUser? = null) : BaseRobot() {

    fun helpMenuIsVisible(): LoginFormResult {
        // Checks if the buttons exist.
        view.withId(R.id.buttonResetPassword).checkDisplayed()
        view.withId(R.id.buttonForgotUser).checkDisplayed()
        view.withId(R.id.buttonLoginProblems).checkDisplayed()
        view.withId(R.id.buttonGetSupport).checkDisplayed()

        //clicks the Cancel button
        clickElement<LoginFormResult>(view
            .withId(R.id.md_buttonDefaultNegative)
            .withText(R.string.cancel)
        )

        //checks if the login screen reappears
        checkInputDisplayed(R.id.inputEmail)
        checkInputDisplayed(R.id.inputPassword)
        view.withId(R.id.buttonLogin).checkDisplayed()
        return this
    }

    fun passwordIsVisible(): LoginFormResult {
        view
            .instanceOf(EditText::class.java)
            .withId(R.id.inputPassword)
            .withText(user!!.password)
            .checkDisplayed()
        return this
    }
}
