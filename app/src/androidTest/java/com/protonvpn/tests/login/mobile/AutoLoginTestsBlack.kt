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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.protonvpn.robots.mobile.HomeRobot
import com.protonvpn.interfaces.verify
import com.protonvpn.android.managed.AutoLoginConfig
import com.protonvpn.android.managed.ManagedConfig
import com.protonvpn.robots.mobile.LoginRobotVpn
import com.protonvpn.test.shared.TestUserEndToEnd
import com.protonvpn.testRules.CommonRuleChains.realBackendComposeRule
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import me.proton.core.test.android.robots.auth.AddAccountRobot
import me.proton.core.test.quark.Quark
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject
import javax.inject.Singleton

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class AutoLoginTestsBlack {
    @Module
    @InstallIn(SingletonComponent::class)
    class AutoLoginModule {
        @Provides
        @Singleton
        fun provideManagedConfig(): ManagedConfig =
            ManagedConfig(autoLoginConfig)
    }

    @Inject
    lateinit var quark: Quark
    @Inject
    lateinit var testUserEndToEnd: TestUserEndToEnd

    @get:Rule
    val rule = realBackendComposeRule()

    @Before
    fun setUp() {
        quark.jailUnban()
    }

    @Test
    fun successfulAutoLogin() {
        val plusUser = testUserEndToEnd.plusUser
        autoLoginConfig.value = AutoLoginConfig.UsernamePassword(plusUser.email, plusUser.password)
        HomeRobot.verify { isLoggedIn() }
    }

    @Test
    fun wrongCredentialsAutoLogin() {
        val badUser = testUserEndToEnd.badUser
        autoLoginConfig.value = AutoLoginConfig.UsernamePassword(badUser.email, badUser.password)
        HomeRobot.verify { autoLoginIncorrectCredentialsIsDisplayed() }
    }

    @Test
    fun retryIncorrectLogin() {
        val badUser = testUserEndToEnd.badUser
        val plusUser = testUserEndToEnd.plusUser
        autoLoginConfig.value = AutoLoginConfig.UsernamePassword(badUser.email, badUser.password)
        HomeRobot.verify { autoLoginIncorrectCredentialsIsDisplayed() }

        autoLoginConfig.value = AutoLoginConfig.UsernamePassword(plusUser.email, plusUser.password)
        HomeRobot.verify { isLoggedIn() }
    }

    @Test
    fun autoLoggedInUserIsChangedToAnotherOne() {
        val freeUser = testUserEndToEnd.freeUser
        val plusUser = testUserEndToEnd.plusUser
        autoLoginConfig.value = AutoLoginConfig.UsernamePassword(freeUser.email, freeUser.password)
        HomeRobot.verify { isLoggedIn() }

        autoLoginConfig.value = AutoLoginConfig.UsernamePassword(plusUser.email, plusUser.password)
        HomeRobot.verify { isPlusUserLoggedIn() }
    }

    @Test
    fun alreadyLoggedInUserIsChangedToIncorrectCredentialsOne() {
        val badUser = testUserEndToEnd.badUser
        val plusUser = testUserEndToEnd.plusUser
        autoLoginConfig.value = AutoLoginConfig.UsernamePassword(plusUser.email, plusUser.password)
        HomeRobot.verify { isLoggedIn() }

        autoLoginConfig.value = AutoLoginConfig.UsernamePassword(badUser.email, badUser.password)
        HomeRobot.verify { autoLoginIncorrectCredentialsIsDisplayed() }
    }

    @Test
    fun manualLoginThenAutoLogin(){
        val freeUser = testUserEndToEnd.freeUser
        val plusUser = testUserEndToEnd.plusUser
        AddAccountRobot().signIn()
        LoginRobotVpn.signIn(plusUser)
        HomeRobot.verify { isLoggedIn() }

        autoLoginConfig.value = AutoLoginConfig.UsernamePassword(freeUser.email, freeUser.password)
        HomeRobot.verify { isFreeUserLoggedIn() }
    }

    companion object AutoLogin {
        var autoLoginConfig: MutableStateFlow<AutoLoginConfig?> =
            MutableStateFlow(null)
    }
}
