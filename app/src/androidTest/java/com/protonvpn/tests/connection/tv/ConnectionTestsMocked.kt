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
import androidx.test.filters.LargeTest
import com.protonvpn.android.servers.api.LogicalsResponse
import com.protonvpn.android.servers.api.SERVER_FEATURE_SECURE_CORE
import com.protonvpn.android.tv.main.TvMainActivity
import com.protonvpn.android.vpn.DisconnectTrigger
import com.protonvpn.mocks.TestApiConfig
import com.protonvpn.robots.tv.TvCountryListRobot
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createLogicalServer
import com.protonvpn.testRules.ProtonHiltAndroidRule
import com.protonvpn.testRules.SetLoggedInUserRule
import com.protonvpn.testRules.TV_MAIN_SCREEN_REDESIGN_VARIANTS
import com.protonvpn.testRules.TvMainScreenRedesignRule
import com.protonvpn.testsHelper.ServerManagerHelper
import dagger.hilt.android.testing.HiltAndroidTest
import me.proton.test.fusion.ui.compose.FusionComposeTest
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * [ConnectionTestsMocked] Contains all tests related to Connection actions.
 * Tests for the legacy-only screens are in [ConnectionTestsMockedLegacyUi].
 */
@RunWith(Parameterized::class)
@LargeTest
@HiltAndroidTest
class ConnectionTestsMocked(
    private val isTvMainScreenRedesign: Boolean
) : FusionComposeTest() {

    companion object {
        private const val US = "US"
        private const val NEW_YORK = "New York"

        @JvmStatic
        @Parameterized.Parameters(name = "redesign={0}")
        fun data() = TV_MAIN_SCREEN_REDESIGN_VARIANTS
    }

    private val activityRule = ActivityScenarioRule(TvMainActivity::class.java)
    // Same servers as the default mock API config (the binary status data matches 4 servers) but with a city.
    private val apiConfig = TestApiConfig.Mocked(TestUser.plusUser) {
        rule(get, path eq "/vpn/v2/logicals") {
            val servers = listOf(
                createLogicalServer(
                    "CH1",
                    exitCountry = "CH",
                    entryCountry = "US",
                    statusIndex = 0u,
                    features = SERVER_FEATURE_SECURE_CORE
                ),
                createLogicalServer("PL1", exitCountry = "PL", statusIndex = 1u),
                createLogicalServer("US1", exitCountry = US, city = NEW_YORK, statusIndex = 2u),
                createLogicalServer("AR1", exitCountry = "AR", statusIndex = 3u),
            )
            respond(LogicalsResponse("StatusID", servers))
        }
    }
    private val protonHiltRule = ProtonHiltAndroidRule(this, apiConfig)

    @get:Rule
    val rules = RuleChain
        .outerRule(protonHiltRule)
        .around(SetLoggedInUserRule(TestUser.plusUser))
        .around(TvMainScreenRedesignRule(protonHiltRule, isTvMainScreenRedesign, TestUser.plusUser))
        .around(activityRule)

    private val homeRobot = TvCountryListRobot()

    @Before
    fun setUp() {
        activityRule.scenario
        homeRobot.waitUntilCountryIsLoaded(US)
        if (!isTvMainScreenRedesign) {
            // Delay to allow for UI to fully load. TV UI is quite outdated,
            // so it requires quite some work on client side to make tests not used this timeout.
            // VPNAND-1941
            Thread.sleep(10000)
        }
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
        assumeTrue(isTvMainScreenRedesign)
        homeRobot
            .connectToCountry(US)
            .verify {
                userIsConnected()
                countryTileIsConnected(US)
            }
        homeRobot
            .disconnectFromCountry()
            .verify { userIsDisconnected() }
    }

    @Test
    fun connectToCity() {
        assumeTrue(isTvMainScreenRedesign)
        homeRobot
            .connectToCity(US, NEW_YORK)
            .verify {
                userIsConnected()
                userIsConnectedToCity(US, NEW_YORK)
                countryTileIsConnected(US)
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
            .navigateToHome()
            .verify { connectionStatusDidNotChange(connectionStatus) }
    }

    @After
    fun tearDown() {
        ServerManagerHelper().vpnConnectionManager.disconnect(DisconnectTrigger.Test("test tear down"))
    }
}
