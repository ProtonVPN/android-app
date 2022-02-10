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

package com.protonvpn.testsTv.tests.login

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.tv.TvLoginActivity
import com.protonvpn.android.tv.login.TvLoginViewModel
import com.protonvpn.di.MockApi
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testRules.ProtonHiltAndroidRule
import com.protonvpn.testsHelper.UserDataHelper
import com.protonvpn.testsTv.actions.TvLoginRobot
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import me.proton.core.network.domain.ApiResult
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * [LoginTestsTv] Contains all tests related to Login actions.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@HiltAndroidTest
class LoginTestsTv {

    private val activityRule = ActivityScenarioRule(TvLoginActivity::class.java)
    private val hiltRule = ProtonHiltAndroidRule(this)
    @get:Rule val rules = RuleChain
        .outerRule(hiltRule)
        .around(activityRule)

    private val loginRobot = TvLoginRobot()
    private lateinit var userDataHelper: UserDataHelper

    @Inject
    lateinit var injectedApi: ProtonApiRetroFit
    lateinit var mockApi: MockApi

    @Before
    fun setUp() {
        hiltRule.inject()
        mockApi = injectedApi as MockApi
        // Login tests start with mock API in logged out state.
        mockApi.forkedUserResponse =
            ApiResult.Error.Http(TvLoginViewModel.Companion.HTTP_CODE_KEEP_POLLING, "")
        userDataHelper = UserDataHelper()
        activityRule.scenario
    }

    @Test
    fun loginHappyPath() {
        loginRobot
            .signIn()
        mockApi.forkedUserResponse = ApiResult.Success(TestUser.forkedSessionResponse)
        loginRobot
            .waitUntilLoggedIn()
            .verify { userIsLoggedIn() }
    }

    @Test
    fun loginCodeIsDisplayed() {
        loginRobot.signIn()
            .waitUntilLoginCodeIsDisplayed()
            .verify { loginCodeViewIsDisplayed() }
    }

    @After
    fun tearDown() {
        runBlocking(Dispatchers.Main) {
            userDataHelper.userData.onLogout()
        }
    }
}
