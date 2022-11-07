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
import com.protonvpn.android.appconfig.SessionForkSelectorResponse
import com.protonvpn.android.tv.TvLoginActivity
import com.protonvpn.android.tv.login.TvLoginViewModel
import com.protonvpn.mocks.MockInterceptorWrapper
import com.protonvpn.mocks.TestApiConfig
import com.protonvpn.mocks.respond
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testRules.ProtonHiltAndroidRule
import com.protonvpn.testsHelper.UserDataHelper
import com.protonvpn.testsTv.actions.TvLoginRobot
import dagger.hilt.android.testing.HiltAndroidTest
import okhttp3.mock.endsWith
import okhttp3.mock.get
import okhttp3.mock.path
import okhttp3.mock.rule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import javax.inject.Inject

private const val FORK_SELECTOR = "fork_selector"
private const val FORK_USER_CODE = "1234ABCD"

/**
 * [LoginTestsTv] Contains all tests related to Login actions.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@HiltAndroidTest
class LoginTestsTv {

    // Login tests start with mock API in logged out state.
    private val mockApiConfig = TestApiConfig.Mocked(TestUser.plusUser) {
        rule(get, path endsWith "/auth/sessions/forks") {
            respond(SessionForkSelectorResponse(FORK_SELECTOR, FORK_USER_CODE))
        }

        rule(get, path endsWith "/auth/sessions/forks/$FORK_SELECTOR", times = Int.MAX_VALUE) {
            respond(TvLoginViewModel.HTTP_CODE_KEEP_POLLING)
        }
    }

    private val activityRule = ActivityScenarioRule(TvLoginActivity::class.java)
    private val hiltRule = ProtonHiltAndroidRule(this, mockApiConfig)

    @get:Rule
    val rules = RuleChain
        .outerRule(hiltRule)
        .around(activityRule)

    private val loginRobot = TvLoginRobot()
    private lateinit var userDataHelper: UserDataHelper

    @Inject
    lateinit var mockApi: MockInterceptorWrapper

    @Before
    fun setUp() {
        hiltRule.inject()
        userDataHelper = UserDataHelper()
        activityRule.scenario
    }

    @Test
    fun loginHappyPath() {
        loginRobot
            .signIn()
        mockApi.prependRules {
            rule(get, path endsWith "/auth/sessions/forks/$FORK_SELECTOR") {
                respond(TestUser.forkedSessionResponse)
            }
        }
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
}
