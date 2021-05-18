/*
 * Copyright (c) 2021 Proton Technologies AG
 * This file is part of Proton Technologies AG and ProtonCore.
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

package com.protonvpn.base

import androidx.annotation.IdRes
import androidx.annotation.StringRes
import com.protonvpn.android.ProtonApplication

/**
 * [BaseVerify] Contains common view independent verification methods
 */
open class BaseVerify : BaseRobot(){

    inline fun <reified T> checkIfElementIsDisplayedByStringId(@StringRes resId: Int): T = executeAndReturnRobot{
        view
                .withText(resId)
                .checkDisplayed()
    }

    inline fun <reified T> checkIfElementIsDisplayedById(@IdRes Id: Int): T = executeAndReturnRobot{
        view
                .withId(Id)
                .checkDisplayed()
    }

    inline fun <reified T> checkIfElementByIdContainsText(@IdRes id: Int, @StringRes resId: Int): T = executeAndReturnRobot{
        view
                .withId(id)
                .checkContains(ProtonApplication.getAppContext().getString(resId))
    }

    inline fun <reified T> checkIfElementByIdContainsText(@IdRes id: Int, text: String): T  = executeAndReturnRobot{
        view
                .withId(id)
                .checkContains(text)
    }

    inline fun <reified T> checkIfElementIsNotDisplayedByStringId(@StringRes resId: Int): T = executeAndReturnRobot{
        view
                .withText(resId)
                .checkDoesNotExist()
    }
}
