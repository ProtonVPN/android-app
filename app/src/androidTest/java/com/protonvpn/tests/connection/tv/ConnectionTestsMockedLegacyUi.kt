/*
 * Copyright (c) 2026. Proton AG
 *
 * This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.protonvpn.tests.connection.tv

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.protonvpn.android.tv.main.TvMainActivity
import com.protonvpn.android.vpn.DisconnectTrigger
import com.protonvpn.mocks.TestApiConfig
import com.protonvpn.robots.tv.TvHomeRobot
import com.protonvpn.robots.tv.TvDetailedCountryRobot
import com.protonvpn.robots.tv.TvServerListRobot
import com.protonvpn.test.shared.MockedServers
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testRules.ProtonHiltAndroidRule
import com.protonvpn.testRules.SetLoggedInUserRule
import com.protonvpn.testRules.TvMainScreenRedesignRule
import com.protonvpn.testsHelper.ServerManagerHelper
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Connection tests for screens that exist only in the legacy TV main screen (country details, server list,
 * favourites).
 * Remove together with the TvMainScreenRedesign feature flag.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@HiltAndroidTest
class ConnectionTestsMockedLegacyUi {

    private val activityRule = ActivityScenarioRule(TvMainActivity::class.java)
    private val protonHiltRule = ProtonHiltAndroidRule(this, TestApiConfig.Mocked(TestUser.plusUser))

    @get:Rule
    val rules = RuleChain
        .outerRule(protonHiltRule)
        .around(SetLoggedInUserRule(TestUser.plusUser))
        .around(TvMainScreenRedesignRule(protonHiltRule, isEnabled = false, user = TestUser.plusUser))
        .around(activityRule)

    private val homeRobot = TvHomeRobot()
    private val countryRobot = TvDetailedCountryRobot()
    private val serverListRobot = TvServerListRobot()

    @Before
    fun setUp() {
        activityRule.scenario
        homeRobot.waitUntilCountryIsLoaded(MockedServers.serverList[0].exitCountry)
        // Delay to allow for UI to fully load. TV UI is quite outdated,
        // so it requires quite some work on client side to make tests not used this timeout.
        // VPNAND-1941
        Thread.sleep(10000)
    }

    @Test
    fun connectToCountry() {
        homeRobot
            .openFirstCountryConnectionWindow()
        countryRobot
            .connectToStreamingCountry()
            .verify { disconnectButtonIsDisplayed() }
        countryRobot
            .disconnectFromCountry()
            .verify { connectButtonIsDisplayed() }
    }

    @Test
    fun connectViaServerList() {
        homeRobot
            .openFirstCountryConnectionWindow()
        countryRobot
            .openServerList()
        serverListRobot
            .connectToServer()
            .verify { disconnectButtonIsDisplayed() }
        serverListRobot
            .disconnectFromServer()
            .verify { disconnectButtonIsNotDisplayed() }
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
                userIsConnectedToCountry(countryName)
            }
        homeRobot
            .disconnectFromCountry()
            .verify { userIsDisconnected() }
    }

    @After
    fun tearDown() {
        ServerManagerHelper().vpnConnectionManager.disconnect(DisconnectTrigger.Test("test tear down"))
    }
}
