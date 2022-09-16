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
package com.protonvpn.tests.map

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import com.protonvpn.actions.HomeRobot
import com.protonvpn.actions.MapRobot
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.annotations.TestID
import com.protonvpn.data.DefaultData
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testRules.EspressoDispatcherRule
import com.protonvpn.testRules.LoginTestRule
import com.protonvpn.testRules.ProtonHiltAndroidRule
import com.protonvpn.testRules.ProtonHomeActivityTestRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * [MapTests] contains tests related to map view
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class MapTests {

    private val testRule = ProtonHomeActivityTestRule()
    private val homeRobot = HomeRobot()
    private val mapRobot = MapRobot()

    private val hiltRule = ProtonHiltAndroidRule(this)

    @get:Rule
    var rules = RuleChain
        .outerRule(hiltRule)
        .around(LoginTestRule(TestUser.plusUser))
        .around(testRule)
        .around(EspressoDispatcherRule())

    @Test
    @TestID(77)
    fun mapNodeSuccessfullySelected() {
        testRule.mockStatusOnConnect(VpnState.Connected)
        homeRobot.swipeLeftToOpenMap()
        mapRobot.clickOnCountryNodeUntilConnectButtonAppears(DefaultData.TEST_COUNTRY)
            .clickConnectButton()
            .verify { isConnected() }
        homeRobot.swipeDownToCloseConnectionInfoLayout()
        mapRobot.verify { isCountryNodeSelected(DefaultData.TEST_COUNTRY) }
    }

    @Test
    @TestID(103966)
    @SdkSuppress(minSdkVersion = 28)
    fun mapNodeIsNotSelected() {
        testRule.mockStatusOnConnect(VpnState.Connecting)
        homeRobot.swipeLeftToOpenMap()
        mapRobot.clickOnCountryNodeUntilConnectButtonAppears(DefaultData.TEST_COUNTRY)
            .clickConnectButtonWithoutVpnHandling()
            .clickCancelConnectionButton()
        mapRobot.verify {
            isDisconnectedFromVpn()
            isCountryNodeNotSelected(DefaultData.TEST_COUNTRY)
        }
    }
}
