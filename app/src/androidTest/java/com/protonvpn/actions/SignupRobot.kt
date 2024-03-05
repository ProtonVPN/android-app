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

package com.protonvpn.actions

import com.protonvpn.base.BaseRobot
import me.proton.core.auth.presentation.R as AuthR

class SignupRobot : BaseRobot(){
    fun enterRecoveryEmail(email: String) : SignupRobot {
        replaceText<SignupRobot>(AuthR.id.email, email)
        view.withId(AuthR.id.next).hasSibling(view.withId(AuthR.id.terms)).click()
        return this
    }
}