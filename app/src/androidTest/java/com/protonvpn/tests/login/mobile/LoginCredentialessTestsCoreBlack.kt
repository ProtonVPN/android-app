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

package com.protonvpn.tests.login.mobile

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import com.protonvpn.robots.mobile.HomeRobot
import com.protonvpn.robots.mobile.SettingsRobot
import com.protonvpn.interfaces.verify
import com.protonvpn.android.redesign.app.ui.MainActivity
import com.protonvpn.robots.mobile.HumanVerificationRobot
import com.protonvpn.robots.mobile.OnboardingRobot
import com.protonvpn.robots.mobile.SignupRobot
import com.protonvpn.testRules.CommonRuleChains.realBackendRule
import dagger.hilt.android.testing.HiltAndroidTest
import me.proton.core.auth.test.MinimalSignInGuestTests
import me.proton.core.auth.test.fake.FakeIsCredentialLessEnabled
import me.proton.core.auth.test.robot.CredentialLessWelcomeRobot
import me.proton.core.auth.test.robot.signup.SetPasswordRobot
import me.proton.core.auth.test.robot.signup.SignUpRobot
import me.proton.core.test.quark.Quark
import me.proton.core.util.kotlin.random
import me.proton.test.fusion.FusionConfig
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import javax.inject.Inject

@LargeTest
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 28) // HV robot doesn't work well on older Android versions
@HiltAndroidTest
class LoginCredentialessTestsCoreBlack : MinimalSignInGuestTests {

    @get:Rule
    val rule: RuleChain = realBackendRule()
        .around(object : ExternalResource() {
            override fun before() {
                isCredentialLessEnabled.localEnabled = true
                isCredentialLessEnabled.remoteDisabled = suspend { false }
            }
        })
        .around(createAndroidComposeRule<MainActivity>().also {
            FusionConfig.Compose.testRule.set(it)
        })

    @Inject
    lateinit var isCredentialLessEnabled: FakeIsCredentialLessEnabled

    @Inject
    override lateinit var quark: Quark

    @Test
    override fun credentialLessToRegularAccount() {
        CredentialLessWelcomeRobot.clickContinueAsGuest()
        verifyAfterCredentialLessSignup()

        navigateToSignupFromCredentialLess()

        val testUsername = "test-${String.random()}"
        SignUpRobot.forExternal().clickSwitch()
        SignUpRobot
            .forInternal()
            .fillUsername(testUsername)
            .clickNext()
        SetPasswordRobot
            .fillAndClickNext("pAsword132Test#_Abcd")

        SignupRobot.enterRecoveryEmail("${testUsername}@proton.ch")
        HumanVerificationRobot.verifyViaEmail()

        verifyAfterRegularSignup(testUsername)
    }

    override fun navigateToSignupFromCredentialLess() {
        HomeRobot
            .navigateToSettings()
            .createAccount()
    }

    override fun verifyAfterCredentialLessSignup() {
        OnboardingRobot
            .verify { onboardingPaymentIdDisplayed() }
            .skipOnboardingPayment()
        HomeRobot.verify {
            isHomeDisplayed()
            isLoggedIn()
        }
    }

    override fun verifyAfterRegularSignup(username: String) {
        OnboardingRobot
            .verify { onboardingPaymentIdDisplayed() }
            .skipOnboardingPayment()
        HomeRobot
            .navigateToSettings() // The main screen is reset to the Home tab as the VpnUser is refetched.
            .verify { usernameIsDisplayed(username) }
    }
}
