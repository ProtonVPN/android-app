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

package com.protonvpn.tests.login.tv

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.protonvpn.android.tv.main.TvMainActivity
import com.protonvpn.mocks.TestApiConfig
import com.protonvpn.robots.tv.TvCountryListRobot
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testRules.ProtonHiltAndroidRule
import com.protonvpn.testRules.SetLoggedInUserRule
import com.protonvpn.testsHelper.UserDataHelper
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * [LogoutTestsMocked] Contains all tests related to Logout actions.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@HiltAndroidTest
class LogoutTestsMocked {
    private val activityRule = ActivityScenarioRule(TvMainActivity::class.java)

    @get:Rule
    val rules = RuleChain.outerRule(
        ProtonHiltAndroidRule(
            this,
            TestApiConfig.Mocked(TestUser.plusUser)
        )
    )
        .around(SetLoggedInUserRule(TestUser.plusUser))
        .around(activityRule)

    private lateinit var homeRobot: TvCountryListRobot
    private lateinit var userDataHelper: UserDataHelper

    @Before
    fun setUp() {
        homeRobot = TvCountryListRobot()
        userDataHelper = UserDataHelper()
        activityRule.scenario
    }

    @Test
    fun logoutHappyPath() {
        homeRobot
            .signOut()
            .confirmSignOut()
            .verify { signInButtonIsDisplayed() }
    }
}