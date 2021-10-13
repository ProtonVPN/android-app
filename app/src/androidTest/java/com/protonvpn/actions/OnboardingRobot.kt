/*
 * Copyright (c) 2018 Proton Technologies AG
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

import com.protonvpn.android.R
import com.protonvpn.base.BaseRobot
import com.protonvpn.base.BaseVerify
import me.proton.core.presentation.ui.view.ProtonButton

class OnboardingRobot : BaseRobot() {

    fun swipeLeft(): OnboardingRobot = swipeLeftOnElementById(R.id.action_bar_root)

    fun swipeRight(): OnboardingRobot = swipeRightOnElementById(R.id.action_bar_root)

    fun clickSkip(): OnboardingRobot = clickElement(R.id.buttonSkip)

    fun clickNext(): OnboardingRobot = clickElement(R.id.buttonNext)

    fun clickSignUp(): OnboardingRobot = clickElement(R.id.buttonSignup)

    fun pressLogin(): OnboardingRobot = clickElement(R.id.buttonLogin)

    fun waitUntilFirstOnboardingScreenIsOpened() : OnboardingRobot =
            waitUntilDisplayedByText(R.string.onboardingStartSlideTitle)

    class Verify : BaseVerify(){
        fun signUpButtonHasLink(browserPackageName: String): Verify = checkIfBrowserIsOpened(browserPackageName)

        fun isTextWeBelieveIsDisplayed(): Verify =
            waitUntilDisplayedByText(R.string.onboardingMiddleSlideTitle)

        fun isTextWelcomeIsDisplayed(): Verify =
            checkIfElementIsDisplayedByStringId(R.string.onboardingStartSlideDescription)

        fun isLoginPageDisplayed(): Verify =
            checkIfElementIsDisplayedById(R.id.buttonLogin)

        fun isFinalPageDisplayed(): Verify {
            checkIfElementIsDisplayedByStringId<Verify>(R.string.onboardingEndingSlideDescription)
            checkIfElementIsDisplayedById<Verify>(R.id.buttonLogin)
            checkIfElementIsDisplayedById<Verify>(R.id.buttonSignup)
            return this
        }

        fun onboardingViewIsVisible(): Verify {
            checkIfElementIsDisplayedById<Verify>(R.id.protonLogo)
            checkIfElementIsDisplayedById<Verify>(R.id.buttonSkip)
            checkIfElementIsDisplayedById<Verify>(R.id.buttonNext)
            checkIfElementIsDisplayedByStringId<Verify>(R.string.onboardingStartSlideTitle)
            checkIfElementIsDisplayedByStringId<Verify>(R.string.onboardingStartSlideDescription)
            return this
        }
    }

    inline fun verify(block: Verify.() -> Unit) = Verify().apply(block)
}