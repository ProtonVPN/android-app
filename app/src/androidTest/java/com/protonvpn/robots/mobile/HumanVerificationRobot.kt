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

import android.view.KeyEvent
import android.webkit.WebView
import androidx.test.espresso.web.assertion.WebViewAssertions.webContent
import androidx.test.espresso.web.matcher.DomMatchers.hasElementWithId
import androidx.test.espresso.web.sugar.Web.onWebView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.protonvpn.interfaces.Robot
import java.util.concurrent.TimeUnit

object HumanVerificationRobot : Robot {
    private val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    fun verifyViaCaptchaSlow(): HumanVerificationRobot {
        // We have no reliable and FAST way to detect if captcha was loaded.
        // Intentionally fail captcha
        onWebView().withTimeout(30_000L, TimeUnit.MILLISECONDS)
            .check(webContent(hasElementWithId("ic-arrows-switch")))
        Thread.sleep(5000)
        uiDevice.pressKeyCode(KeyEvent.KEYCODE_TAB)
        uiDevice.pressEnter()
        // Press retry
        Thread.sleep(1000)
        uiDevice.pressKeyCode(KeyEvent.KEYCODE_TAB)
        uiDevice.pressEnter()
        // Solve captcha. (It helps, because captcha is preloaded and there is no delay in loading it.)
        Thread.sleep(1000)
        uiDevice.pressKeyCode(KeyEvent.KEYCODE_TAB)
        uiDevice.pressEnter()
        return this
    }

    fun verifyViaEmail(): HumanVerificationRobot {
        uiDevice.wait(Until.findObject(By.clazz(WebView::class.java)), 30_000L)
        // Delay to allow for webview to properly load
        Thread.sleep(5000)
        repeat(2) { uiDevice.pressKeyCode(KeyEvent.KEYCODE_TAB) }
        uiDevice.pressEnter()
        uiDevice.wait(Until.findObject(By.text("Get Verification code")), 10_000L)
        uiDevice.pressEnter()
        uiDevice.wait(Until.findObject(By.text("Verify")), 10_000L)
        repeat(6) { uiDevice.pressKeyCode(KeyEvent.KEYCODE_6) }
        uiDevice.pressEnter()
        return this
    }
}