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

import com.protonvpn.android.R
import com.protonvpn.base.BaseRobot
import com.protonvpn.base.BaseVerify
import com.protonvpn.testsTv.actions.TvDetailedCountryRobot

/**
 * [AddAccountRobot] Contains all actions and verifications for first step of login view
 */
class AddAccountRobot : BaseRobot() {
    fun selectSignInOption() : LoginRobot = clickElementById(R.id.sign_in)

    fun selectSignupOption() : SignupRobot = clickElementById(R.id.sign_up)

    class Verify : BaseVerify(){
        fun successfullyLoggedOut() {
            checkIfElementIsDisplayedById(R.id.sign_in)
            checkIfElementIsDisplayedById(R.id.sign_up)
        }
    }

    inline fun verify(block: Verify.() -> Unit) = Verify().apply(block)
}
