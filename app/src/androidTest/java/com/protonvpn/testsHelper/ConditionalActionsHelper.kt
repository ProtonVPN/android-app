/*
 * Copyright (c) 2021 Proton AG
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

package com.protonvpn.testsHelper

import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.test.espresso.Espresso
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.platform.app.InstrumentationRegistry
import com.protonvpn.base.BaseRobot
import org.hamcrest.Matchers

class ConditionalActionsHelper : BaseRobot() {

    fun scrollDownInViewWithIdUntilObjectWithIdAppears(
        @IdRes viewId: Int,
        @IdRes objectId: Int
    ) {
        Espresso.onView(ViewMatchers.withId(objectId)).perform(scrollToEx())
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }

    fun scrollDownInViewWithIdUntilObjectWithIdAppears(
        @IdRes viewId: Int,
        @IdRes objectId: Int,
        clazz: Class<*>?
    ) {
        Espresso.onView(Matchers.allOf(Matchers.instanceOf(clazz), ViewMatchers.withId(objectId)))
            .perform(scrollToEx())
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }

    fun scrollDownInViewWithIdUntilObjectWithTextAppears(@IdRes viewId: Int, text: String?) {
        Espresso.onView(ViewMatchers.withText(Matchers.equalToIgnoringCase(text)))
            .perform(scrollToEx()).check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }

    fun scrollDownInViewWithIdUntilObjectWithTextAppears(
        @IdRes viewId: Int,
        @StringRes textId: Int
    ) {
        scrollDownInViewWithIdUntilObjectWithTextAppears(
            viewId,
            InstrumentationRegistry.getInstrumentation().targetContext.getString(textId)
        )
    }
}