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

package com.protonvpn.tests.signup

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import com.protonvpn.android.redesign.app.ui.MainActivity
import com.protonvpn.robots.mobile.HomeRobot
import com.protonvpn.robots.mobile.HumanVerificationRobot
import com.protonvpn.robots.mobile.OnboardingRobot
import com.protonvpn.robots.mobile.SignupRobot
import com.protonvpn.interfaces.verify
import com.protonvpn.testRules.CommonRuleChains.realBackendRule
import com.protonvpn.testsHelper.TestSetup
import dagger.hilt.android.testing.HiltAndroidTest
import me.proton.core.auth.test.MinimalSignUpExternalTests
import me.proton.core.auth.test.robot.signup.CongratsRobot
import me.proton.core.auth.test.robot.signup.SetPasswordRobot
import me.proton.core.auth.test.robot.signup.SignUpRobot
import me.proton.core.auth.test.robot.signup.SignupInternal
import me.proton.core.auth.test.rule.AcceptExternalRule
import me.proton.core.network.domain.client.ExtraHeaderProvider
import me.proton.core.util.kotlin.random
import me.proton.test.fusion.FusionConfig
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import javax.inject.Inject

@LargeTest
@SdkSuppress(minSdkVersion = 28) // Signups tests does not work on older versions due to animations bug
@HiltAndroidTest
class SignupTests : MinimalSignUpExternalTests {

    @get:Rule
    val rule: RuleChain = realBackendRule()
        .around(AcceptExternalRule { extraHeaderProvider })
        .around(createAndroidComposeRule<MainActivity>().apply {
            FusionConfig.Compose.testRule.set(this)
        })

    @Inject
    lateinit var extraHeaderProvider: ExtraHeaderProvider

    @Before
    fun setUp() {
        TestSetup.quark.jailUnban()
    }

    private val isCongratsDisplayed = false

    @Test
    // TODO Migrate this test to core
    override fun signupSwitchToInternalAccountCreationHappyPath() {
        val testUsername = "test-${String.random()}"

        SignUpRobot
            .forExternal()
            .clickSwitch()

        SignUpRobot
            .forInternal()
            .fillUsername(testUsername)
            .clickNext()
        SetPasswordRobot
            .fillAndClickNext("123123123")

        SignupRobot.enterRecoveryEmail("${testUsername}@proton.ch")
        HumanVerificationRobot.verifyViaEmail()

        CongratsRobot.takeIf { isCongratsDisplayed }?.apply {
            uiElementsDisplayed()
            clickStart()
        }

        verifyAfter()
    }

    @Test
    override fun signupExternalAccountHappyPath() {
        val testEmail = "${String.random()}@example.com"

        SignupInternal
            .apply {
                robotDisplayed()
            }
            .fillEmail(testEmail)
            .clickNext()
            .fillCode()
            .clickVerify()

        SetPasswordRobot
            .fillAndClickNext(String.random(12))

        CongratsRobot.takeIf { isCongratsDisplayed }?.apply {
            uiElementsDisplayed()
            clickStart()
        }

        verifyAfter()
    }

    private fun verifyAfter() {
        OnboardingRobot
            .verify { welcomeScreenIsDisplayed() }
            .closeWelcomeDialog()
            .verify { onboardingPaymentIdDisplayed() }
            .skipOnboardingPayment()
        HomeRobot.isHomeDisplayed()
    }
}