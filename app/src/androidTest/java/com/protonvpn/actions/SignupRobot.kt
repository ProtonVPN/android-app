/*
 *  Copyright (c) 2022 Proton AG
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

package com.protonvpn.actions

import androidx.annotation.IdRes
import androidx.test.espresso.web.sugar.Web.onWebView
import androidx.test.espresso.web.webdriver.DriverAtoms.findElement
import androidx.test.espresso.web.webdriver.DriverAtoms.webClick
import androidx.test.espresso.web.webdriver.Locator
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.protonvpn.android.R
import com.protonvpn.base.BaseRobot
import com.protonvpn.base.BaseVerify
import java.util.concurrent.TimeUnit

class SignupRobot : BaseRobot() {

    fun enterUsername(username: String): SignupRobot {
        replaceText<SignupRobot>(R.id.usernameInput, username)
        return clickElementById(R.id.nextButton)
    }

    fun enterPassword(password: String): SignupRobot {
        replaceText<SignupRobot>(R.id.passwordInput, password)
        replaceText<SignupRobot>(R.id.confirmPasswordInput, password)
        clickOnNextButtonBySibling(R.id.confirmPasswordInput)
        return this
    }

    fun enterRecoveryEmail(recoveryEmail: String): SignupRobot {
        replaceText<SignupRobot>(R.id.emailEditText, recoveryEmail)
        clickOnNextButtonBySibling(R.id.fragmentOptionsContainer)
        return this
    }

    fun verifyViaEmail(code: String): OnboardingRobot {
        onWebView()
            .withElement(findElement(Locator.ID, "label_1"))
            .perform(webClick())
            .withElement(findElement(Locator.XPATH, "//*[@id=\"key_1\"]/button"))
            .perform(webClick())
        Thread.sleep(6000)

        // Workaround for inputing code
        // as inputing with webkeys for some reason does not work correctly on code verification page
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        repeat(7) { uiDevice.pressKeyCode(13) }
        onWebView()
            .withElement(findElement(Locator.XPATH, "/html/body/div[1]/main/button[1]"))
            .perform(webClick())

        return OnboardingRobot()
    }

    private fun clickOnNextButtonBySibling(@IdRes siblingId: Int) {
        view.withId(R.id.nextButton).hasSibling(view.withId(siblingId)).click()
    }

    class Verify : BaseVerify() {

    }

    inline fun verify(block: Verify.() -> Unit) = Verify().apply(block)
}
