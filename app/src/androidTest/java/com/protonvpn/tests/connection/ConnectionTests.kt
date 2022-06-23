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
import androidx.test.filters.SdkSuppress
import com.protonvpn.actions.ConnectionRobot
import com.protonvpn.actions.CountriesRobot
import com.protonvpn.actions.HomeRobot
import com.protonvpn.actions.MapRobot
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.vpn.ErrorType
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.annotations.TestID
import com.protonvpn.data.DefaultData
import com.protonvpn.test.shared.MockedServers
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testRules.EspressoDispatcherRule
import com.protonvpn.testRules.ProtonHiltAndroidRule
import com.protonvpn.testRules.ProtonHomeActivityTestRule
import com.protonvpn.testRules.SetUserPreferencesRule
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
        .outerRule(ProtonHiltAndroidRule(this))
        .around(SetUserPreferencesRule(TestUser.plusUser))
        .around(EspressoDispatcherRule())
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
    @TestID(58)
    fun connectAndDisconnectViaQuickConnect() {
        testRule.mockStatusOnConnect(VpnState.Connected)
        homeRobot.connectThroughQuickConnect(DefaultData.DEFAULT_CONNECTION_PROFILE)
            .verify { isConnected() }
        connectionRobot.disconnectFromVPN()
            .verify { isDisconnected() }
    }

    @Test
    @TestID(61)
    fun connectAndDisconnectViaCountryList() {
        testRule.mockStatusOnConnect(VpnState.Connected)
        val country = MockedServers.serverList[0].displayName
        countriesRobot.selectCountry(country)
            .clickConnectButton("fastest")
            .verify { isConnected() }
        connectionRobot.disconnectFromVPN()
            .verify { isDisconnected() }
    }

    @Test
    @TestID(56)
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
    @TestID(63)
    fun connectAndDisconnectViaProfiles() {
        testRule.mockStatusOnConnect(VpnState.Connected)
        homeRobot.swipeLeftToOpenProfiles()
            .clickOnConnectButtonUntilConnected(DefaultData.DEFAULT_CONNECTION_PROFILE)
        connectionRobot.verify { isConnected() }
        connectionRobot.disconnectFromVPN()
            .verify { isDisconnected() }
    }

    @Test
    @TestID(67)
    fun cancelWhileConnecting() {
        testRule.mockStatusOnConnect(VpnState.Connecting)
        homeRobot.connectThroughQuickConnect(DefaultData.DEFAULT_CONNECTION_PROFILE)
            .clickCancelConnectionButton()
            .verify { isDisconnected() }
    }

    @Test
    @TestID(66)
    fun connectToServerWhenInternetIsDown() {
        testRule.mockErrorOnConnect(ErrorType.UNREACHABLE)
        homeRobot.connectThroughQuickConnect(DefaultData.DEFAULT_CONNECTION_PROFILE)
        connectionRobot.verify { isNotReachableErrorDisplayed() }
        connectionRobot.clickCancelRetry()
            .verify { isDisconnected() }
    }

    @Test
    @TestID(121429)
    @SdkSuppress(minSdkVersion = 28)
    fun connectAndDisconnectViaQuickConnectCustomProfile() {
        testRule.mockStatusOnConnect(VpnState.Connected)
        serviceTestHelper.addProfile(
            VpnProtocol.Smart,
            DefaultData.PROFILE_NAME,
            customProfileServerDomain
        )
        homeRobot.connectThroughQuickConnect(DefaultData.PROFILE_NAME)
            .verify { isConnected() }
        connectionRobot.disconnectFromVPN()
            .verify { isDisconnected() }
    }

    @Test
    @TestID(121738)
    fun serverInformationLegendDocumentsAllTypes(){
        homeRobot.clickOnInformationIcon()
            .verify { serverInfoLegendDescribesAllServerTypes() }
    }
}
