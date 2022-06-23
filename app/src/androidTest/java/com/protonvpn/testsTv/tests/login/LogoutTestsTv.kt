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
import com.protonvpn.android.tv.TvLoginActivity
import com.protonvpn.testRules.EspressoDispatcherRule
import com.protonvpn.testRules.ProtonHiltAndroidRule
import com.protonvpn.testsHelper.ServiceTestHelper
import com.protonvpn.testsHelper.UserDataHelper
import com.protonvpn.testsTv.actions.TvCountryListRobot
import com.protonvpn.testsTv.actions.TvLoginRobot
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * [LogoutTestsTv] Contains all tests related to Logout actions.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@HiltAndroidTest
class LogoutTestsTv {

    private val activityRule = ActivityScenarioRule(TvLoginActivity::class.java)
    @get:Rule val rules = RuleChain
        .outerRule(ProtonHiltAndroidRule(this))
        .around(EspressoDispatcherRule())
        .around(activityRule)

    private lateinit var loginRobot: TvLoginRobot
    private lateinit var homeRobot: TvCountryListRobot
    private lateinit var serviceTestHelper: ServiceTestHelper
    private lateinit var userDataHelper: UserDataHelper

    @Before
    fun setUp() {
        loginRobot = TvLoginRobot()
        homeRobot = TvCountryListRobot()
        serviceTestHelper = ServiceTestHelper()
        userDataHelper = UserDataHelper()
        activityRule.scenario
        loginRobot
                .signIn()
                .waitUntilLoggedIn()
    }

    @Test
    fun logoutHappyPath() {
        homeRobot
                .signOut()
                .confirmSignOut()
                .verify { signInButtonIsDisplayed() }
    }

    @Test
    fun logoutWhileConnectedToServer() {
        homeRobot
                .connectToRecommendedCountry()
                .signOut()
                .verify { signOutWhileConnectedWarningMessageIsDisplayed() }
    }

    @Test
    fun cancelLogoutWhileConnectedToServer() {
        homeRobot
                .connectToRecommendedCountry()

        val connectionStatus = homeRobot.getConnectionStatus()

        homeRobot
                .signOut()
                .cancelSignOut()
                .verify { connectionStatusDidNotChange(connectionStatus) }
    }

    @After
    fun tearDown() {
        runBlocking(Dispatchers.Main) {
            serviceTestHelper.connectionManager.disconnect("test tear down")
        }
    }
}
