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

import com.protonvpn.interfaces.Robot
import me.proton.core.test.android.instrumented.matchers.inputFieldMatcher
import me.proton.test.fusion.Fusion.view
import me.proton.core.auth.presentation.R as AuthR

object SignupRobot : Robot {
    private val emailInput get() = view.withCustomMatcher(inputFieldMatcher(AuthR.id.email))
    private val nextButton get() = view.withId(AuthR.id.next)

    fun enterRecoveryEmail(email: String){
        emailInput.clearText().typeText(email)
        nextButton.click()
    }
}