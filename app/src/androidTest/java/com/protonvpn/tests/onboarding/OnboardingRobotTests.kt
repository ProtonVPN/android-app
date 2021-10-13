/*
 * Copyright (c) 2021 Proton Technologies AG
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
package com.protonvpn.tests.onboarding

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.FlakyTest
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import com.protonvpn.android.ui.onboarding.OnboardingActivity
import com.protonvpn.actions.OnboardingRobot
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import okhttp3.internal.wait
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class OnboardingRobotTests {

    private val activityRule = ActivityScenarioRule(OnboardingActivity::class.java)
    private val onboardingRobot = OnboardingRobot()

    @get:Rule
    var rules = RuleChain
            .outerRule(HiltAndroidRule(this))
            .around(activityRule)

    @Test
    fun checkIfOnboardingViewIsVisible() {
        onboardingRobot
                .verify {
                    onboardingViewIsVisible()
                }
    }

    @Test
    fun checkIfSwipeLeftWorks() {
        onboardingRobot
                .waitUntilFirstOnboardingScreenIsOpened()
                .swipeLeft()
                .verify {
                    isTextWeBelieveIsDisplayed()
                }
    }

    @Test
    fun checkIfSwipeRightWorks() {
        onboardingRobot
                .swipeLeft()
                .swipeRight()
                .verify {
                    isTextWelcomeIsDisplayed()
                }
    }

    @Test
    fun skipOnboarding() {
        onboardingRobot
                .clickSkip()
                .verify {
                    isLoginPageDisplayed()
                }
    }

    @Test
    fun checkIfButtonNextWorks() {
        onboardingRobot
                .clickNext()
                .verify {
                    isTextWeBelieveIsDisplayed()
                }
    }

    @Test
    fun checkIfFinalPageDisplayed() {
        onboardingRobot
                .clickNext()
                .clickNext()
                .verify {
                    isFinalPageDisplayed()
                }
    }

    @Test
    @FlakyTest
    fun checkIfSignUpNavigatesToCorrectLink() {
        onboardingRobot
                .clickNext()
                .clickNext()
                .clickSignUp()
                .verify {
                    signUpButtonHasLink("com.android.chrome")
                }
    }

    // Currently skipped on Android 5-6, because animation Fails to load button fully. Flaky also on higher
    // versions.
    @SdkSuppress(minSdkVersion = 24)
    @Test
    @FlakyTest
    fun checkIfLoginButtonWorks() {
        onboardingRobot
                .clickNext()
                .clickNext()
                .pressLogin()
                .verify {
                    isLoginPageDisplayed()
                }
    }
}