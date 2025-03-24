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

package com.protonvpn.base

import android.view.View
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers
import me.proton.core.test.android.robots.CoreRobot
import org.hamcrest.Matcher

/**
 * [BaseRobot] Contains common actions for views
 */
open class BaseRobot : CoreRobot() {

    inline fun <reified T> clickElementByText(@StringRes resId: Int): T =
        executeAndReturnRobot {
            view
                .withVisibility(ViewMatchers.Visibility.VISIBLE)
                .withText(resId)
                .click()
        }

    inline fun <reified T> clickElementById(@IdRes id: Int): T =
        executeAndReturnRobot {
            view
                .withVisibility(ViewMatchers.Visibility.VISIBLE)
                .withId(id)
                .click()
        }

    inline fun <reified T> clickDialogElementByText(@StringRes textRes: Int): T =
        executeAndReturnRobot {
            view.withText(textRes)
                .inRoot(rootView.isDialog())
                .checkDisplayed()
                .click()
        }

    inline fun <reified T> clickElementByText(text: String): T =
        executeAndReturnRobot {
            view
                .withText(text)
                .click()
        }

    inline fun <reified T> clickElementByIdAndText(@IdRes id: Int, @StringRes resId: Int): T =
        executeAndReturnRobot {
            view
                .withId(id)
                .withText(resId)
                .click()
        }

    inline fun <reified T> clickElementByContentDescription(text: String): T =
        executeAndReturnRobot {
            view
                .withContentDesc(text)
                .click()
        }

    inline fun <reified T> clickElementByContentDescription(@StringRes resId: Int): T =
        executeAndReturnRobot {
            view
                .withContentDesc(resId)
                .click()
        }

    inline fun <reified T> swipeLeftOnElementById(@IdRes id: Int): T =
        executeAndReturnRobot {
            view
                .withId(id)
                .swipeLeft()
        }

    inline fun <reified T> swipeDownOnElementById(@IdRes id: Int): T =
        executeAndReturnRobot {
            view
                .withId(id)
                .swipeDown()
        }

    inline fun <reified T> clickElementByIdAndContentDescription(
        @IdRes id: Int,
        description: String
    ): T =
        executeAndReturnRobot {
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

    inline fun <reified T> waitUntilDisplayed(@IdRes id: Int): T =
        executeAndReturnRobot {
            view
                .withId(id)
                .checkDisplayed()
        }

    inline fun <reified T> waitUntilDisplayedByText(@StringRes resId: Int): T =
        executeAndReturnRobot {
            view
                .withText(resId)
                .checkDisplayed()
        }

    inline fun <reified T> waitUntilDisplayedByText(text: String): T =
        executeAndReturnRobot {
            view
                .withText(text)
                .checkDisplayed()
        }

    inline fun <reified T> waitUntilDisplayedByContentDesc(@StringRes resId: Int): T =
        executeAndReturnRobot {
            view
                .withContentDesc(resId)
                .checkDisplayed()
        }

    inline fun <reified T> pressBack(@IdRes id: Int): T =
        executeAndReturnRobot {
            view
                .withId(id)
                .pressBack()
        }

    inline fun <reified T> longClickByCustomMatcher(matcher: Matcher<View>): T =
        executeAndReturnRobot {
            view
                .withCustomMatcher(matcher)
                .longClick()
        }

    fun getText(matcher: ViewInteraction): String {
        var text = String()
        matcher.perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> {
                return ViewMatchers.isAssignableFrom(TextView::class.java)
            }

            override fun getDescription(): String {
                return "Gets text from element."
            }

            override fun perform(uiController: UiController, view: View) {
                val textView = view as TextView
                text = textView.text.toString()
            }
        })

        return text
    }

    inline fun <reified T> executeAndReturnRobot(block: () -> Unit): T {
        block()
        return T::class.java.newInstance()
    }
}
