/*
 * Copyright (c) 2021 Proton Technologies AG
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
package com.protonvpn.tests.connection

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.protonvpn.actions.ServiceRobot
import com.protonvpn.android.vpn.ErrorType
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.data.DefaultData
import com.protonvpn.kotlinActions.ConnectionRobot
import com.protonvpn.kotlinActions.CountriesRobot
import com.protonvpn.kotlinActions.HomeRobot
import com.protonvpn.kotlinActions.MapRobot
import com.protonvpn.test.shared.MockedServers
import com.protonvpn.test.shared.TestUser
import com.protonvpn.tests.testRules.ProtonHomeActivityTestRule
import com.protonvpn.tests.testRules.SetUserPreferencesRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class ConnectionRobotTests {

    private val testRule = ProtonHomeActivityTestRule()

    @get:Rule
    var rules = RuleChain
        .outerRule(HiltAndroidRule(this))
        .around(SetUserPreferencesRule(TestUser.plusUser))
        .around(testRule)

    private lateinit var homeRobot: HomeRobot
    private lateinit var countriesRobot: CountriesRobot
    private lateinit var mapRobot: MapRobot
    private lateinit var connectionRobot: ConnectionRobot

    @Before
    fun setup() {
        homeRobot = HomeRobot()
        countriesRobot = CountriesRobot()
        mapRobot = MapRobot()
        connectionRobot = ConnectionRobot()
    }

    @Test
    fun connectAndDisconnectViaQuickConnect() {
        testRule.mockStatusOnConnect(VpnState.Connected)
        homeRobot.connectThroughQuickConnect(DefaultData.DEFAULT_PROFILE)
            .verify { isConnected() }
        connectionRobot.disconnectFromVPN()
            .verify { isDisconnected() }
    }

    @Test
    fun connectAndDisconnectViaCountryList() {
        testRule.mockStatusOnConnect(VpnState.Connected)
        val country = MockedServers.serverList[0].displayName
        countriesRobot.selectCountry(country)
            .connectToFastestServer()
            .verify { isConnected() }
        connectionRobot.disconnectFromVPN()
            .verify { isDisconnected() }
    }

    @Test
    fun connectAndDisconnectViaMapView() {
        testRule.mockStatusOnConnect(VpnState.Connected)
        homeRobot.swipeLeftToOpenMap()
        mapRobot.clickOnCountryNode(DefaultData.TEST_COUNTRY)
            .clickConnectButton()
            .verify { isConnected() }
        connectionRobot.disconnectFromVPN()
            .verify { isDisconnected() }
    }

    @Test
    fun connectAndDisconnectViaProfiles() {
        testRule.mockStatusOnConnect(VpnState.Connected)
        homeRobot.swipeLeftToOpenProfiles()
            .clickOnConnectButtonUntilConnected(DefaultData.DEFAULT_PROFILE)
        connectionRobot.verify { isConnected() }
        connectionRobot.disconnectFromVPN()
            .verify { isDisconnected() }
    }

    @Test
    fun cancelWhileConnecting() {
        testRule.mockStatusOnConnect(VpnState.Connecting)
        homeRobot.connectThroughQuickConnect(DefaultData.DEFAULT_PROFILE)
            .clickCancelConnectionButton()
            .verify { isDisconnected() }
    }

    @Test
    fun connectToServerWhenInternetIsDown() {
        testRule.mockErrorOnConnect(ErrorType.UNREACHABLE)
        homeRobot.connectThroughQuickConnect(DefaultData.DEFAULT_PROFILE)
        connectionRobot.verify { isNotReachableErrorDisplayed() }
        connectionRobot.clickCancelRetry()
            .verify { isDisconnected() }
    }
}