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
package com.protonvpn.actions

import com.protonvpn.android.R
import com.protonvpn.results.LoginFormResult
import com.protonvpn.results.LoginResult
import com.protonvpn.test.shared.TestUser
import me.proton.core.test.android.robots.CoreRobot

class LoginRobot : CoreRobot() {
    fun clickOnNeedHelpButton(): LoginFormResult =
        clickElement(view
            .withId(R.id.buttonNeedHelp)
            .withText(R.string.loginNeedHelp)
        )

    fun login(user: TestUser): LoginResult {
        replaceText<LoginRobot>(R.id.inputEmail, user.email)
        replaceText<LoginRobot>(R.id.inputPassword, user.password)
        clickElement<LoginRobot>(view
            .withId(R.id.buttonLogin)
            .withText(R.string.login)
        )
        return LoginResult(user)
    }

    fun viewUserPassword(user: TestUser): LoginFormResult {
        replaceText<LoginRobot>(R.id.inputEmail, user.email)
        replaceText<LoginRobot>(R.id.inputPassword, user.password)
        clickElement<LoginRobot>(
            view
                .isDescendantOf(view.withId(R.id.inputPassword))
                .withId(com.google.android.material.R.id.text_input_end_icon)
        )
        return LoginFormResult(user)
    }
}
