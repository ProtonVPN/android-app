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
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testRules.CommonRuleChains.realBackendComposeRule
import com.protonvpn.testsHelper.AtlasEnvVarHelper
import com.protonvpn.testsHelper.TestSetup
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.serialization.SerializationException
import me.proton.core.auth.test.robot.login.LoginLegacyRobot
import me.proton.core.auth.test.robot.login.LoginRobot
import me.proton.core.test.android.robots.auth.AddAccountRobot
import me.proton.core.test.quark.data.User
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URLDecoder
import java.net.URLEncoder


/**
 * [LoginTestsBlack] contains UI tests for Login flow
 */

@LargeTest
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class LoginTestsBlack {

    @get:Rule
    val rule = realBackendComposeRule()

    private lateinit var addAccountRobot: AddAccountRobot

    @Before
    fun setUp() {
        TestSetup.quark.jailUnban()
        addAccountRobot = AddAccountRobot()
        AddAccountRobot().signIn()
    }

    @Test
    fun loginWithPlusUser() {
        LoginRobotVpn.signIn(TestUser.plusUser)
        HomeRobot.verify { isLoggedIn() }
    }

    @Test
    fun loginWithIncorrectCredentials() {
        LoginRobotVpn.signIn(TestUser.badUser)
            .verify { incorrectLoginCredentialsIsShown() }
    }

    @Test
    fun viewPasswordIsVisible() {
        LoginLegacyRobot.fillUsername(TestUser.plusUser.email)
            .fillPassword(TestUser.plusUser.password)
        LoginRobotVpn
            .viewPassword()
            .verify { passwordIsVisible(TestUser.plusUser) }
    }

    @Test
    fun needHelpMenuIsOpened() {
        LoginRobotVpn.selectNeedHelp()
            .verify { needHelpOptionsAreDisplayed() }
    }

    @Test
    fun rememberMeFunctionality() {
        LoginRobotVpn.signIn(TestUser.plusUser)
        HomeRobot.verify { isLoggedIn() }
            .logout()
        addAccountRobot.signIn()
            .verify { view.withText(TestUser.plusUser.email).checkDisplayed() }
    }

    @Test
    //Can't complete captcha on API23 due to animations bug in test framework.
    @SdkSuppress(minSdkVersion = 28)
    fun loginWithHumanVerification() {
        AtlasEnvVarHelper().withAtlasEnvVar({ forceCaptchaOnLogin() }) {
            LoginRobotVpn.signIn(TestUser.plusUser)
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
            TestSetup.quark?.userCreate(specialCharsUser)
        } catch (e: SerializationException) {
            // The test environment returns a non-JSON response when the user already exists.
            // TODO: remove once the test environment is fixed.
            Log.e("LoginTests", "Error when decoding Quark command response", e)
        }
        LoginRobot.login(
            specialCharsUser.name,
            URLDecoder.decode(specialCharsUser.password, "utf-8")
        )
        HomeRobot.verify { isLoggedIn() }
    }
}