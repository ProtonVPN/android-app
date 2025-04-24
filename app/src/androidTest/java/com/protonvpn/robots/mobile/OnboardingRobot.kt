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

import com.protonvpn.android.R
import com.protonvpn.data.Timeouts
import com.protonvpn.interfaces.Robot
import me.proton.test.fusion.Fusion.node
import me.proton.test.fusion.Fusion.view

object OnboardingRobot : Robot {
    private val getStartedButton get() = node.withText(R.string.onboarding_welcome_action)
    private val skipOnboardingButton get() = view.withId(R.id.buttonNotNow)
    private val welcomeLabel get() = node.withText(R.string.onboarding_welcome_title)
    private val upgradePrivacyLabel get() = view.withText(R.string.upgrade_plus_title)

    fun closeWelcomeDialog(): OnboardingRobot {
        getStartedButton.click()
        return this
    }

    fun skipOnboardingPayment(): OnboardingRobot {
        skipOnboardingButton.click()
        return this
    }

    fun welcomeScreenIsDisplayed() : OnboardingRobot {
        welcomeLabel.await(Timeouts.ONE_MINUTE ) { assertIsDisplayed() }
        return this
    }

    fun onboardingPaymentIdDisplayed() : OnboardingRobot {
        upgradePrivacyLabel.await(Timeouts.ONE_MINUTE) { checkIsDisplayed() }
        return this
    }
}