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

import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.common.truth.Truth.assertThat
import com.protonvpn.matchers.ProtonMatcher
import me.proton.core.test.android.instrumented.ui.espresso.OnView

/**
 * [BaseVerify] Contains common view independent verification methods
 */
open class BaseVerify : BaseRobot() {

    fun checkIfElementIsDisplayedById(@IdRes Id: Int) =
        view.withId(Id).checkDisplayed()

    fun checkIfElementIsNotDisplayedById(@IdRes Id: Int) =
        view.withId(Id).checkNotDisplayed()

    fun checkIfElementByIdContainsText(@IdRes id: Int, text: String) =
        view.withId(id).checkContains(text)

    fun checkIfElementIsNotDisplayedByStringId(@StringRes resId: Int) =
        view.withText(resId).checkDoesNotExist()

    fun checkIfElementIsNotDisplayedByText(text: String) =
        view.withText(text).checkDoesNotExist()

    fun checkIfElementIsDisplayedByContentDesc(text: String) =
        view.withContentDesc(text).checkDisplayed()

    fun checkIfElementDoesNotExistByContentDesc(text: String) =
        view.withContentDesc(text).checkDoesNotExist()

    fun checkIfElementDoesNotExistById(@IdRes id: Int) =
        view.withId(id).checkDoesNotExist()

    fun checkIfElementIsDisplayedByStringId(@StringRes resId: Int) =
        view.withVisibility(ViewMatchers.Visibility.VISIBLE).withText(resId).checkDisplayed()

    fun checkIfElementIsDisplayedByHtmlStringId(@StringRes resId: Int) =
        view.withVisibility(ViewMatchers.Visibility.VISIBLE).withCustomMatcher(ProtonMatcher.withHtmlText(resId))

    fun checkIfElementIsDisplayedByText(text: String) =
        view.withText(text).checkDisplayed()

    fun checkIfElementIsDisplayedByText(text: String, clazz: Class<*>) =
        view.instanceOf(clazz).withText(text).checkDisplayed()

    fun checkIfDialogContainsText(@StringRes textRes: Int): OnView =
        view.withText(textRes)
            .inRoot(rootView.isDialog())
            .checkDisplayed()


    fun checkIfElementIsChecked(@IdRes id: Int) = view.withId(id).checkIsChecked()

    fun checkIfElementIsNotChecked(@IdRes id: Int) = view.withId(id).checkIsNotChecked()

    fun checkIfBrowserIsOpened(browserPackageName: String) {
        val myDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val currentPackage = myDevice.currentPackageName
        assertThat(currentPackage).isEqualTo(browserPackageName)
    }
}
