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

import android.widget.EditText
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import me.proton.core.presentation.ui.view.ProtonInput
import me.proton.core.test.android.robots.CoreRobot

/**
 * [BaseRobot] Contains common actions for views
 */
open class BaseRobot : CoreRobot() {

    inline fun <reified T> clickElement(@IdRes id: Int, @StringRes text: Int): T = executeAndReturnRobot {
        clickElement(view.withId(id).withText(text))
    }

    inline fun <reified T> clickElementByText(@StringRes resId: Int): T = executeAndReturnRobot {
        view
                .withText(resId)
                .click()
    }

    inline fun <reified T> clickElementById(@IdRes id: Int): T = executeAndReturnRobot{
        view
                .withId(id)
                .click()
    }

    inline fun <reified T> clickElementByText(text: String): T = executeAndReturnRobot{
        view
                .withText(text)
                .click()
    }

    inline fun <reified T> clickElementByContentDescription(text: String): T = executeAndReturnRobot {
        view
                .withContentDesc(text)
                .click()
    }

    inline fun <reified T> clickElementByIdAndContentDescription(
        @IdRes id: Int,
        description: String
    ): T = executeAndReturnRobot {
        view
                .withId(id)
                .withContentDesc(description)
                .click()
    }

    inline fun <reified T> clickElementByIndexInParent(
        @IdRes parentId: Int,
        index: Int
    ): T = executeAndReturnRobot {
        view
                .withParent(view.withId(parentId))
                .withParentIndex(index)
                .click()
    }

    inline fun <reified T> clearText(
        @IdRes id: Int,
        clazz: Class<*> = EditText::class.java
    ): T = executeAndReturnRobot {
        view
                .instanceOf(clazz)
                .withId(id)
                .clearText()
    }

    inline fun <reified T> waitUntilDisplayed(@IdRes id: Int, time : Long): T = executeAndReturnRobot{
        view
                .withId(id)
                .wait(time)
    }

    inline fun <reified T> waitUntilDisplayedByText(@StringRes resId: Int, time : Long = 10_000): T  = executeAndReturnRobot{
        view
                .withText(resId)
                .wait(time)
    }

    inline fun <reified T> scrollToObjectByResId(@StringRes resId: Int): T = executeAndReturnRobot{
        view
                .withText(resId)
                .scrollTo()
    }

    inline fun <reified T> clickOnRecycleViewByPosition(@IdRes id: Int, position: Int): T = executeAndReturnRobot{
        recyclerView
                .withId(id)
                .onItemAtPosition(position)
                .doubleClick()
    }

    inline fun <reified T> pressBack(@IdRes id: Int): T = executeAndReturnRobot{
        view
                .withId(id)
                .pressBack()
    }

    inline fun <reified T> longClickByText(@StringRes resId : Int): T = executeAndReturnRobot{
        view
                .withText(resId)
                .longClick()
    }

    fun checkInputDisplayed(@IdRes id: Int) {
        view.instanceOf(ProtonInput::class.java).withId(id).checkDisplayed()
    }

    fun checkInputNotDisplayed(@IdRes id: Int) {
        view.instanceOf(ProtonInput::class.java).withId(id).checkNotDisplayed()
    }

    inline fun <reified T> executeAndReturnRobot(block: () -> Unit): T {
        block()
        return T::class.java.newInstance()
    }
}
