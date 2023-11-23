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

import com.protonvpn.android.R
import com.protonvpn.base.BaseRobot
import com.protonvpn.base.BaseVerify
import com.protonvpn.data.Timeouts

class OnboardingRobot : BaseRobot() {

    fun proceedToOnboardingNextStep(): OnboardingRobot = clickElementById(R.id.next)

    fun completeOnboarding(withTelemetryScreen: Boolean): OnboardingRobot {
        val onboardingSteps = if (withTelemetryScreen) 2 else 1
        repeat(onboardingSteps) {
            proceedToOnboardingNextStep()
        }
        return this
    }

    fun skipOnboarding(): HomeRobot = clickElementById(R.id.skip)

    class Verify : BaseVerify() {
        fun welcomeScreenIsDisplayed() {
            view.withText(R.string.onboarding_welcome_title).withTimeout(Timeouts.LONG_MS)
                .checkDisplayed()
            checkIfElementIsDisplayedByStringId(R.string.onboarding_welcome_description)
        }
    }

    inline fun verify(block: Verify.() -> Unit) = Verify().apply(block)
}
