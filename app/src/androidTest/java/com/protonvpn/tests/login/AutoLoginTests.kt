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

package com.protonvpn.tests.login

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.protonvpn.actions.LoginRobot
import com.protonvpn.actions.compose.HomeRobot
import com.protonvpn.actions.compose.interfaces.verify
import com.protonvpn.android.R
import com.protonvpn.android.managed.AutoLoginConfig
import com.protonvpn.android.managed.ManagedConfig
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testRules.CommonRuleChains.realBackendComposeRule
import com.protonvpn.testsHelper.TestSetup
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import me.proton.core.test.android.robots.auth.AddAccountRobot
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Singleton

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class AutoLoginTests {
    private lateinit var loginRobot: LoginRobot

    @Module
    @InstallIn(SingletonComponent::class)
    class AutoLoginModule {
        @Provides
        @Singleton
        fun provideManagedConfig(): ManagedConfig =
            ManagedConfig(autoLoginConfig)
    }

    @get:Rule
    val rule = realBackendComposeRule()

    @Before
    fun setUp() {
        TestSetup.quark.jailUnban()
        loginRobot = LoginRobot()
    }

    @Test
    fun successfulAutoLogin() {
        autoLoginConfig.value = AutoLoginConfig(TestUser.plusUser.email, TestUser.plusUser.password)
        HomeRobot.verify { isLoggedIn() }
    }

    @Test
    fun wrongCredentialsAutoLogin() {
        autoLoginConfig.value = AutoLoginConfig(TestUser.badUser.email, TestUser.badUser.password)
        HomeRobot.verify { autoLoginIncorrectCredentialsIsDisplayed() }
    }

    @Test
    fun retryIncorrectLogin() {
        autoLoginConfig.value = AutoLoginConfig(TestUser.badUser.email, TestUser.badUser.password)
        HomeRobot.verify { autoLoginIncorrectCredentialsIsDisplayed() }

        autoLoginConfig.value = AutoLoginConfig(TestUser.plusUser.email, TestUser.plusUser.password)
        HomeRobot.verify { isLoggedIn() }
    }

    @Test
    fun autoLoggedInUserIsChangedToAnotherOne() {
        autoLoginConfig.value = AutoLoginConfig(TestUser.plusUser.email, TestUser.plusUser.password)
        HomeRobot.verify { isLoggedIn() }

        autoLoginConfig.value = AutoLoginConfig(TestUser.visionaryBlack.email, TestUser.visionaryBlack.password)
        HomeRobot.navigateToSettings()
            .verify { usernameIsDisplayed(TestUser.visionaryBlack.email) }
    }

    @Test
    fun alreadyLoggedInUserIsChangedToIncorrectCredentialsOne() {
        autoLoginConfig.value = AutoLoginConfig(TestUser.plusUser.email, TestUser.plusUser.password)
        HomeRobot.verify { isLoggedIn() }

        autoLoginConfig.value = AutoLoginConfig(TestUser.badUser.email, TestUser.badUser.password)
        HomeRobot.verify { autoLoginIncorrectCredentialsIsDisplayed() }
    }

    @Test
    fun manualLoginThenAutoLogin(){
        AddAccountRobot().signIn()
        loginRobot.signIn(TestUser.plusUser)
        HomeRobot.verify { isLoggedIn() }

        autoLoginConfig.value = AutoLoginConfig(TestUser.visionaryBlack.email, TestUser.visionaryBlack.password)
        HomeRobot.navigateToSettings()
            .verify { usernameIsDisplayed(TestUser.visionaryBlack.email) }
    }

    companion object AutoLogin {
        var autoLoginConfig: MutableStateFlow<AutoLoginConfig?> =
            MutableStateFlow(null)
    }
}