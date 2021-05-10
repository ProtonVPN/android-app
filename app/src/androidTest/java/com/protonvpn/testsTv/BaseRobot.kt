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

package com.protonvpn.testsTv

import androidx.annotation.IdRes
import androidx.annotation.StringRes
import me.proton.core.test.android.instrumented.CoreRobot
import org.strongswan.android.logic.StrongSwanApplication.getContext

/**
 * [BaseRobot] Contains common actions for views
 */
open class BaseRobot : CoreRobot {

    inline fun <reified T> clickElementByStringId(@StringRes resId: Int): T {
        view
                .withText(getContext().getString(resId))
                .click()
        return T::class.java.newInstance()
    }

    inline fun <reified T> clickElementById(@IdRes id: Int): T {
        view
                .withId(id)
                .click()
        return T::class.java.newInstance()
    }

    inline fun <reified T> waitUntilDisplayed(@IdRes id: Int): T {
        view
                .withId(id)
                .wait()
        return T::class.java.newInstance()
    }

    inline fun <reified T> checkIfElementDisplayedByStringId(@StringRes resId: Int): T {
        view
                .withText(getContext().getString(resId))
                .checkDisplayed()
        return T::class.java.newInstance()
    }

    inline fun <reified T> checkIfElementByIdContainsTextByResId(@IdRes id: Int, @StringRes resId: Int): T {
        view
                .withId(id)
                .checkContains(getContext().getString(resId))
        return T::class.java.newInstance()
    }
}
