/*
 * Copyright (c) 2021 Proton AG
 * This file is part of Proton AG and ProtonCore.
 *
 * ProtonCore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonCore is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonCore.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.protonvpn.testsTv.actions

import com.protonvpn.android.R
import com.protonvpn.base.BaseRobot
import com.protonvpn.base.BaseVerify
import me.proton.core.test.android.instrumented.ui.espresso.OnView

/**
 * [TvLoginRobot] Contains all actions and verifications for login view
 */
class TvLoginRobot : BaseRobot() {

    fun waitUntilLoggedIn(): TvCountryListRobot = waitUntilDisplayedByText(R.string.quickConnect)
    fun waitUntilLoginCodeIsDisplayed(): TvLoginRobot =
            waitUntilDisplayedByText(R.string.tv_login_step3_description)

    fun signIn(): TvLoginRobot {
        waitUntilDisplayedByText<TvLoginRobot>(R.string.tv_login_welcome_button)
        clickElementByText<TvLoginRobot>(R.string.tv_login_welcome_button)
        return TvLoginRobot()
    }

    class Verify : BaseVerify(){

        fun loginCodeViewIsDisplayed() = checkIfElementIsDisplayedByStringId(R.string.tv_login_step3_description)

        fun signInButtonIsDisplayed() {
            waitUntilDisplayedByText<OnView>(R.string.tv_login_welcome_button)
            checkIfElementIsDisplayedByStringId(R.string.tv_login_welcome_button)
        }
    }

    inline fun verify(block: Verify.() -> Unit) = Verify().apply(block)
}
