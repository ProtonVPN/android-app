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

import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import com.protonvpn.android.R
import com.protonvpn.android.utils.Constants
import com.protonvpn.base.BaseRobot
import com.protonvpn.base.BaseVerify

/**
 * [AccountRobot] Contains all actions related to account component
 */
class AccountRobot : BaseRobot() {

    fun clickManageAccount(): AccountRobot = clickElementById(R.id.buttonManageAccount)

    class Verify : BaseVerify() {
        fun checkIfCorrectUsernameIsDisplayed(testUser: String) =
            checkIfElementByIdContainsText(R.id.textUser, testUser)

        fun checkIfAccountButtonHasCorrectUrl() {
            intended(hasData(Constants.ACCOUNT_LOGIN_URL + "?utm_source=" + Constants.PROTON_URL_UTM_SOURCE))
        }
    }

    inline fun verify(block: Verify.() -> Unit) = Verify().apply(block)
}
