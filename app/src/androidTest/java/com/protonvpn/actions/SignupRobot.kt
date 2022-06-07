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
import androidx.annotation.StringRes
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.platform.app.InstrumentationRegistry
import com.protonvpn.android.R
import com.protonvpn.android.utils.HtmlTools
import com.protonvpn.base.BaseRobot
import com.protonvpn.base.BaseVerify
import com.protonvpn.testsHelper.ConditionalActionsHelper

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
        //When HV3 will be introduced check if hardcoded selector can be removed
        clickElementByText<SignupRobot>("email")
        clickElementById<SignupRobot>(R.id.getVerificationCodeButton)
        replaceText<SignupRobot>(R.id.verificationCodeEditText, code)
        return clickElementById(R.id.verifyButton)
    }

    private fun clickOnNextButtonBySibling(@IdRes siblingId: Int) {
        view.withId(R.id.nextButton).hasSibling(view.withId(siblingId)).click()
    }

    class Verify : BaseVerify() {

    }

    inline fun verify(block: Verify.() -> Unit) = Verify().apply(block)
}
