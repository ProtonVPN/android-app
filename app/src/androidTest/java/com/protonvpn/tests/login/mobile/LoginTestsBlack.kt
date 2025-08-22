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

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import com.protonvpn.robots.mobile.HomeRobot
import com.protonvpn.interfaces.verify
import com.protonvpn.android.BuildConfig
import com.protonvpn.robots.mobile.HumanVerificationRobot
import com.protonvpn.robots.mobile.LoginRobotVpn
import com.protonvpn.test.shared.TestUserEndToEnd
import com.protonvpn.testRules.CommonRuleChains.realBackendComposeRule
import com.protonvpn.testsHelper.AtlasEnvVarHelper
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.serialization.SerializationException
import me.proton.core.auth.test.robot.CredentialLessWelcomeRobot
import me.proton.core.auth.test.robot.login.LoginLegacyRobot
import me.proton.core.auth.test.robot.login.LoginRobot
import me.proton.core.compose.component.PROTON_OUTLINED_TEXT_INPUT_TAG
import me.proton.core.test.android.robots.auth.AddAccountRobot
import me.proton.core.test.quark.Quark
import me.proton.core.test.quark.data.User
import me.proton.test.fusion.Fusion
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URLDecoder
import java.net.URLEncoder
import javax.inject.Inject

/**
 * [LoginTestsBlack] contains UI tests for Login flow
 */

@LargeTest
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class LoginTestsBlack {

    @get:Rule
    val rule = realBackendComposeRule()

    @Inject
    lateinit var atlasEnvVarHelper: AtlasEnvVarHelper
    @Inject
    lateinit var quark: Quark
    @Inject
    lateinit var testUserEndToEnd: TestUserEndToEnd

    @Before
    fun setUp() {
        quark.jailUnban()
        CredentialLessWelcomeRobot.clickSignIn()
    }

    @Test
    fun loginWithPlusUser() {
        LoginRobotVpn.signIn(testUserEndToEnd.plusUser)
        HomeRobot.verify { isLoggedIn() }
    }

    @Test
    fun loginWithIncorrectCredentials() {
        LoginRobotVpn.signIn(testUserEndToEnd.badUser)
            .verify { incorrectLoginCredentialsIsShown() }
    }

    @Test
    fun rememberMeFunctionality() {
        LoginRobotVpn.signIn(testUserEndToEnd.plusUser)
        HomeRobot.verify { isLoggedIn() }
            .logout()
        CredentialLessWelcomeRobot.clickSignIn()
        // TODO: this should be part of LoginTwoStepRobot.verify
        Fusion.node
            .withTag(PROTON_OUTLINED_TEXT_INPUT_TAG)
            .withText(testUserEndToEnd.plusUser.email)
            .assertIsDisplayed()
    }

    @Test
    //Can't complete captcha on API23 due to animations bug in test framework.
    @SdkSuppress(minSdkVersion = 28)
    fun loginWithHumanVerification() {
        atlasEnvVarHelper.withAtlasEnvVar({ forceCaptchaOnLogin() }) {
            LoginRobotVpn.signIn(testUserEndToEnd.plusUser)
            HumanVerificationRobot.verifyViaCaptchaSlow()
            HomeRobot.verify { isLoggedIn() }
        }
    }

    @Test
    fun loginWithSpecialCharsUser() {
        val specialCharsUser = User(
            name = "testasSpecChars",
            password = URLEncoder.encode(BuildConfig.SPECIAL_CHAR_PASSWORD, "utf-8")
        )
        try {
            quark.userCreate(specialCharsUser)
        } catch (e: SerializationException) {
            // The test environment returns a non-JSON response when the user already exists.
            // TODO: remove once the test environment is fixed.
            Log.e("LoginTests", "Error when decoding Quark command response", e)
        }
        LoginRobot.login(
            specialCharsUser.name,
            URLDecoder.decode(specialCharsUser.password, "utf-8"),
            isLoginTwoStepEnabled = true,
        )
        HomeRobot.verify { isLoggedIn() }
    }
}
