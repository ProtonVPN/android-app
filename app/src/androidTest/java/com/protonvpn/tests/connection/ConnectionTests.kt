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
import com.protonvpn.actions.ConnectionRobot
import com.protonvpn.actions.CountriesRobot
import com.protonvpn.actions.HomeRobot
import com.protonvpn.actions.MapRobot
import com.protonvpn.android.vpn.ErrorType
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.data.DefaultData
import com.protonvpn.mocks.TestApiConfig
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testRules.ProtonHiltAndroidRule
import com.protonvpn.testRules.ProtonHomeActivityTestRule
import com.protonvpn.testRules.SetLoggedInUserRule
import com.protonvpn.testsHelper.ServiceTestHelper
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * [ConnectionRobot] contains tests related to how UI handles VPN connection (Mocked API and connection is used)
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class ConnectionTests {

    private val testRule = ProtonHomeActivityTestRule()
    private val customProfileServerDomain = "ca-02.protonvpn.com"

    @get:Rule
    var rules = RuleChain
        .outerRule(ProtonHiltAndroidRule(this, TestApiConfig.Mocked(TestUser.plusUser)))
        .around(SetLoggedInUserRule(TestUser.plusUser))
        .around(testRule)

    private lateinit var homeRobot: HomeRobot
    private lateinit var countriesRobot: CountriesRobot
    private lateinit var mapRobot: MapRobot
    private lateinit var connectionRobot: ConnectionRobot
    private lateinit var serviceTestHelper: ServiceTestHelper

    @Before
    fun setup() {
        homeRobot = HomeRobot()
        countriesRobot = CountriesRobot()
        mapRobot = MapRobot()
        connectionRobot = ConnectionRobot()
        serviceTestHelper = ServiceTestHelper()
    }

    @Test
    fun connectAndDisconnectViaQuickConnect() {
        testRule.mockStatusOnConnect(VpnState.Connected)
        homeRobot.connectThroughQuickConnect(DefaultData.DEFAULT_CONNECTION_PROFILE)
            .verify { isConnected() }
        connectionRobot.disconnectFromVPN()
            .verify { isDisconnected() }
    }

    @Test
    fun connectAndDisconnectViaMapView() {
        testRule.mockStatusOnConnect(VpnState.Connected)
        homeRobot.swipeLeftToOpenMap()
        mapRobot.clickOnCountryNodeUntilConnectButtonAppears(DefaultData.TEST_COUNTRY)
            .clickConnectButton()
            .verify { isConnected() }
        connectionRobot.disconnectFromVPN()
            .verify { isDisconnected() }
    }

    @Test
    fun connectAndDisconnectViaProfiles() {
        testRule.mockStatusOnConnect(VpnState.Connected)
        homeRobot.swipeLeftToOpenProfiles()
        connectionRobot.clickOnConnectButtonUntilConnected(DefaultData.DEFAULT_CONNECTION_PROFILE)
        connectionRobot.verify { isConnected() }
        connectionRobot.disconnectFromVPN()
            .verify { isDisconnected() }
    }

    @Test
    fun cancelWhileConnecting() {
        testRule.mockStatusOnConnect(VpnState.Connecting)
        homeRobot.connectThroughQuickConnect(DefaultData.DEFAULT_CONNECTION_PROFILE)
            .clickCancelConnectionButton()
            .verify { isDisconnected() }
    }

    @Test
    fun connectToServerWhenInternetIsDown() {
        testRule.mockErrorOnConnect(ErrorType.UNREACHABLE)
        homeRobot.connectThroughQuickConnect(DefaultData.DEFAULT_CONNECTION_PROFILE)
        connectionRobot.verify { isNotReachableErrorDisplayed() }
        connectionRobot.clickCancelRetry()
            .verify { isDisconnected() }
    }

    @Test
    fun serverInformationLegendDocumentsAllTypes(){
        homeRobot.clickOnInformationIcon()
            .verify { serverInfoLegendDescribesAllServerTypes() }
    }
}
