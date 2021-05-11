/*
 * Copyright (c) 2021 Proton Technologies AG
 * This file is part of Proton Technologies AG and ProtonCore.
 *
 * ProtonCore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonCore is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonCore.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.protonvpn.testsTv.login

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.protonvpn.android.tv.TvLoginActivity
import com.protonvpn.testsHelper.UserDataHelper
import com.protonvpn.testsTv.actions.LoginRobot
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [LoginRobotTestsTv] Contains all tests related to Login actions.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class LoginRobotTestsTv {

    private val loginRobot = LoginRobot()

    @get:Rule
    val activityRule = ActivityScenarioRule(TvLoginActivity::class.java)

    @Before
    fun setUp(){
        activityRule.scenario
    }

    @Test
    fun loginHappyPath(){
        loginRobot
                .signIn()
                .waitUntilLoggedIn()
                .verify { userIsLoggedIn() }
    }

    @Test
    fun loginCodeIsDisplayed(){
        loginRobot
                .signIn()
                .verify { loginCodeViewIsDisplayed() }
    }

    @After
    fun tearDown(){
        var userDataHelper = UserDataHelper()
        userDataHelper.logoutUser()
    }
}
