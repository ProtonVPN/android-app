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

package com.protonvpn.tests.signup

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import com.protonvpn.actions.AddAccountRobot
import com.protonvpn.actions.HomeRobot
import com.protonvpn.actions.LoginRobot
import com.protonvpn.actions.OnboardingRobot
import com.protonvpn.android.appconfig.CachedPurchaseEnabled
import com.protonvpn.android.ui.main.MobileMainActivity
import com.protonvpn.data.DefaultData
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testRules.ProtonHiltAndroidRule
import com.protonvpn.testsHelper.TestSetup
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import javax.inject.Inject
import kotlin.random.Random

@LargeTest
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 28) // Signups tests does not work on older versions due to animations bug
@HiltAndroidTest
class SignupTests {

    private lateinit var addAccountRobot: AddAccountRobot
    private lateinit var loginRobot: LoginRobot
    private lateinit var homeRobot: HomeRobot
    private lateinit var onboardingRobot: OnboardingRobot
    private lateinit var testUsername: String
    private val activityRule = ActivityScenarioRule(MobileMainActivity::class.java)
    private val hiltRule = ProtonHiltAndroidRule(this)

    @Inject
    lateinit var purchaseEnabled: CachedPurchaseEnabled

    @get:Rule
    val rules = RuleChain
        .outerRule(ProtonHiltAndroidRule(this))
        .around(activityRule)

    @Before
    fun setUp() {
        hiltRule.inject()
        TestSetup.setCompletedOnboarding()
        TestSetup.clearJails()
        addAccountRobot = AddAccountRobot()
        loginRobot = LoginRobot()
        homeRobot = HomeRobot()
        onboardingRobot = OnboardingRobot()
        testUsername = "automationUser" + (0..100000000).random(Random(System.currentTimeMillis()))
    }

    @Test
    fun signupEmailVerificationFullPath() {
        addAccountRobot.selectSignupOption()
            .enterUsername(testUsername)
            .enterPassword(TestUser.plusUser.password)
            .enterRecoveryEmail("$testUsername@proton.ch")
            .verifyViaEmail(DefaultData.ATLAS_VERIFICATION_CODE)
            .verify { welcomeScreenIsDisplayed() }
        onboardingRobot.completeOnboarding()
            .closeOnboarding(purchaseEnabled())
            .verify { isInMainScreen() }
        homeRobot.openAccountView()
            .verify { checkIfCorrectUsernameIsDisplayed(testUsername) }
    }

    @Test
    fun signupSkipOnboarding() {
        addAccountRobot.selectSignupOption()
            .enterUsername(testUsername)
            .enterPassword(TestUser.plusUser.password)
            .enterRecoveryEmail("$testUsername@proton.ch")
            .verifyViaEmail(DefaultData.ATLAS_VERIFICATION_CODE)
            .verify { welcomeScreenIsDisplayed() }
        onboardingRobot.skipOnboarding()
            .verify { isInMainScreen() }
    }
}
