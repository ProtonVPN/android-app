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

package com.protonvpn.testsTv.tests.connection

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.protonvpn.android.tv.TvLoginActivity
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testRules.ProtonHiltAndroidRule
import com.protonvpn.testsHelper.ServiceTestHelper
import com.protonvpn.testsHelper.UserDataHelper
import com.protonvpn.testsTv.actions.TvCountryListRobot
import com.protonvpn.testsTv.actions.TvDetailedCountryRobot
import com.protonvpn.testsTv.actions.TvLoginRobot
import com.protonvpn.testsTv.actions.TvServerListRobot
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
 * [ConnectionTestsTv] Contains all tests related to Connection actions.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@HiltAndroidTest
class ConnectionTestsTv {

    private val activityRule = ActivityScenarioRule(TvLoginActivity::class.java)
    @get:Rule val rules = RuleChain
        .outerRule(ProtonHiltAndroidRule(this))
        .around(activityRule)

    private val homeRobot = TvCountryListRobot()
    private val countryRobot = TvDetailedCountryRobot()
    private val serverListRobot = TvServerListRobot()
    private val loginRobot = TvLoginRobot()

    private lateinit var userDataHelper: UserDataHelper
    private lateinit var serviceTestHelper: ServiceTestHelper

    @Before
    fun setUp() {
        userDataHelper = UserDataHelper()
        serviceTestHelper = ServiceTestHelper()
        userDataHelper
                .setUserData(TestUser.plusUser)
        activityRule.scenario
        loginRobot
                .signIn()
                .waitUntilLoggedIn()
    }

    @Test
    fun connectToRecommended() {
        homeRobot
                .connectToRecommendedCountry()
                .verify { userIsConnected() }
        homeRobot
                .disconnectFromCountry()
                .verify { userIsDisconnected() }
    }

    @Test
    fun connectToCountry() {
        homeRobot
                .openFirstCountryConnectionWindow()
        countryRobot
                .connectToStreamingCountry()
                .verify { userIsConnected() }
        countryRobot
                .disconnectFromCountry()
                .verify { userIsDisconnectedStreaming() }
    }

    @Test
    fun connectViaServerList() {
        homeRobot
                .openFirstCountryConnectionWindow()
        countryRobot
                .openServerList()
        serverListRobot
                .connectToServer()
                .verify { userIsConnected() }
        serverListRobot
                .disconnectFromServer()
                .verify { userIsDisconnected() }
    }

    @Test
    fun addServerToFavouritesAndConnect() {
        homeRobot
                .openFirstCountryConnectionWindow()

        val countryName = countryRobot.getCountryName()

        countryRobot
                .addServerToFavourites()
                .goBackToCountryListView()
        homeRobot
                .connectToFavouriteCountry()
                .verify {
                    userIsConnected()
                    userIsConnectedToCorrectCountry(countryName)
                }
        homeRobot
                .disconnectFromCountry()
                .verify { userIsDisconnected() }
    }

    @After
    fun tearDown() {
        runBlocking(Dispatchers.Main) {
            serviceTestHelper.connectionManager.disconnect("test tear down")
            userDataHelper.userData.onLogout()
        }
    }
}
