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
        fun signUpButtonHasLink(browserPackageName: String) = checkIfBrowserIsOpened(browserPackageName)

        fun isLoginPageDisplayed() = checkIfElementIsDisplayedById(R.id.buttonLogin)

        fun isTextWeBelieveIsDisplayed(): Verify =
            waitUntilDisplayedByText(R.string.onboardingMiddleSlideTitle)

        fun isTextWelcomeIsDisplayed() =
            checkIfElementIsDisplayedByStringId(R.string.onboardingStartSlideDescription)

        fun isFinalPageDisplayed(){
            checkIfElementIsDisplayedByStringId(R.string.onboardingEndingSlideDescription)
            checkIfElementIsDisplayedById(R.id.buttonLogin)
            checkIfElementIsDisplayedById(R.id.buttonSignup)
        }

        fun onboardingViewIsVisible() {
            checkIfElementIsDisplayedById(R.id.protonLogo)
            checkIfElementIsDisplayedById(R.id.buttonSkip)
            checkIfElementIsDisplayedById(R.id.buttonNext)
            checkIfElementIsDisplayedByStringId(R.string.onboardingStartSlideTitle)
            checkIfElementIsDisplayedByStringId(R.string.onboardingStartSlideDescription)
        }
    }

    inline fun verify(block: Verify.() -> Unit) = Verify().apply(block)
}
