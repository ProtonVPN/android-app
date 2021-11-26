/*
 *  Copyright (c) 2021 Proton AG
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

package com.protonvpn.tests.secureCore

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.data.DefaultData
import com.protonvpn.actions.ConnectionRobot
import com.protonvpn.actions.CountriesRobot
import com.protonvpn.actions.HomeRobot
import com.protonvpn.actions.MapRobot
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testRules.ProtonHomeActivityTestRule
import com.protonvpn.testRules.SetUserPreferencesRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * [SecureCoreTests] contains tests related to Secure Core connection (Mocked API and connection is used)
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class SecureCoreTests {
    private val testRule = ProtonHomeActivityTestRule()

    @get:Rule
    var rules = RuleChain
        .outerRule(HiltAndroidRule(this))
        .around(SetUserPreferencesRule(TestUser.plusUser))
        .around(testRule)

    private lateinit var homeRobot: HomeRobot
    private lateinit var mapRobot: MapRobot
    private lateinit var connectionRobot: ConnectionRobot
    private lateinit var countriesRobot: CountriesRobot

    @Before
    fun setup() {
        homeRobot = HomeRobot()
        mapRobot = MapRobot()
        connectionRobot = ConnectionRobot()
        countriesRobot = CountriesRobot()
    }

    @Test
    fun connectAndDisconnectFromSecureCoreThroughMap() {
        testRule.mockStatusOnConnect(VpnState.Connected)
        homeRobot.setStateOfSecureCoreSwitch(true)
            .swipeLeftToOpenMap()
        mapRobot.clickOnCountryNode("Sweden")
            .verify { isCountryNodeSelected("Sweden") }
        mapRobot.clickOnCountryNodeUntilConnectButtonAppears("Sweden >> France")
            .clickConnectButton()
            .verify { isConnected() }
        connectionRobot.disconnectFromVPN()
            .verify { isDisconnected() }
    }

    @Test
    fun connectAndDisconnectFromSecureCoreThroughQuickConnect() {
        testRule.mockStatusOnConnect(VpnState.Connected)
        homeRobot.setStateOfSecureCoreSwitch(true)
            .connectThroughQuickConnect(DefaultData.DEFAULT_CONNECTION_PROFILE)
            .verify { isConnected() }
        connectionRobot.disconnectFromVPN()
            .verify { isDisconnected() }
    }

    @Test
    fun connectAndDisconnectFromSecureCoreThroughCountryList() {
        testRule.mockStatusOnConnect(VpnState.Connected)
        homeRobot.setStateOfSecureCoreSwitch(true)
        countriesRobot.selectCountry("Finland")
            .clickConnectButton("via Sweden")
            .verify { isConnected() }
        connectionRobot.disconnectFromVPN()
            .verify { isDisconnected() }
    }
}