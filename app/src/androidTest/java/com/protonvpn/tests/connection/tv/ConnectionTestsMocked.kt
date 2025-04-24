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

package com.protonvpn.tests.connection.tv

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.protonvpn.android.tv.main.TvMainActivity
import com.protonvpn.android.vpn.DisconnectTrigger
import com.protonvpn.mocks.TestApiConfig
import com.protonvpn.test.shared.MockedServers
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testRules.ProtonHiltAndroidRule
import com.protonvpn.testRules.SetLoggedInUserRule
import com.protonvpn.testsHelper.ServerManagerHelper
import com.protonvpn.testsHelper.UserDataHelper
import com.protonvpn.robots.tv.TvCountryListRobot
import com.protonvpn.robots.tv.TvDetailedCountryRobot
import com.protonvpn.robots.tv.TvServerListRobot
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * [ConnectionTestsMocked] Contains all tests related to Connection actions.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@HiltAndroidTest
class ConnectionTestsMocked {

    private val activityRule = ActivityScenarioRule(TvMainActivity::class.java)
    @get:Rule
    val rules = RuleChain
        .outerRule(ProtonHiltAndroidRule(this, TestApiConfig.Mocked(TestUser.plusUser)))
        .around(SetLoggedInUserRule(TestUser.plusUser))
        .around(activityRule)

    private val homeRobot = TvCountryListRobot()
    private val countryRobot = TvDetailedCountryRobot()
    private val serverListRobot = TvServerListRobot()

    private lateinit var userDataHelper: UserDataHelper

    @Before
    fun setUp() {
        userDataHelper = UserDataHelper()
        activityRule.scenario
        homeRobot.waitUntilCountryIsLoaded(MockedServers.serverList[0].exitCountry)
        // Delay to allow for UI to fully load. TV UI is quite outdated,
        // so it requires quite some work on client side to make tests not used this timeout.
        // VPNAND-1941
        Thread.sleep(10000)
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
        ServerManagerHelper().vpnConnectionManager.disconnect(DisconnectTrigger.Test("test tear down"))
    }
}
