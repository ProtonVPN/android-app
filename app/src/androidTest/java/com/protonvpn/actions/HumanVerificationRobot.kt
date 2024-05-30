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

import android.view.KeyEvent
import android.webkit.WebView
import androidx.test.espresso.web.assertion.WebViewAssertions.webContent
import androidx.test.espresso.web.matcher.DomMatchers.hasElementWithId
import androidx.test.espresso.web.sugar.Web.onWebView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.protonvpn.base.BaseRobot

class HumanVerificationRobot : BaseRobot() {

    private val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    fun verifyViaCaptcha() : OnboardingRobot {
        uiDevice.wait(Until.findObject(By.clazz(WebView::class.java)), 30_000L)
        onWebView().check(webContent(hasElementWithId("ic-arrows-switch")))
        repeat(1) { uiDevice.pressKeyCode(KeyEvent.KEYCODE_TAB) }
        uiDevice.pressEnter()
        return OnboardingRobot()
    }

    fun verifyViaEmail() : OnboardingRobot {
        uiDevice.wait(Until.findObject(By.clazz(WebView::class.java)), 30_000L)
        repeat(2) { uiDevice.pressKeyCode(KeyEvent.KEYCODE_TAB) }
        uiDevice.pressEnter()
        uiDevice.wait(Until.findObject(By.text("Get Verification code")), 10_000L)
        uiDevice.pressEnter()
        uiDevice.wait(Until.findObject(By.text("Verify")), 10_000L)
        repeat(6) {uiDevice.pressKeyCode(KeyEvent.KEYCODE_6)}
        uiDevice.pressEnter()
        return OnboardingRobot()
    }
}