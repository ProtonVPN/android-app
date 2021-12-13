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
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.kotlinActions.HomeRobot
import com.protonvpn.kotlinActions.MapRobot
import com.protonvpn.test.shared.TestUser
import com.protonvpn.tests.testRules.ProtonHomeActivityTestRule
import com.protonvpn.tests.testRules.SetUserPreferencesRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class MapRobotTests {

    private val testRule = ProtonHomeActivityTestRule()
    private val homeRobot = HomeRobot()
    private val mapRobot = MapRobot()
    private val country = "United States"

    @get:Rule
    var rules = RuleChain
            .outerRule(HiltAndroidRule(this))
            .around(SetUserPreferencesRule(TestUser.getPlusUser()))
            .around(testRule)

    @Test
    fun mapNodeSuccessfullySelected() {
        testRule.mockStatusOnConnect(VpnState.Connected)
        homeRobot.swipeLeftToOpenMap()
        mapRobot.clickOnCountryNode(country)
                .clickConnectButton()
                .verify {
                    isConnectedToVPN()
                }
        mapRobot.swipeDownToCloseConnectionInfoLayout()
                .verify {
                    isCountryNodeSelected(country)
                }
    }

    @Test
    @SdkSuppress(minSdkVersion = 28)
    fun mapNodeIsNotSelected() {
        testRule.mockStatusOnConnect(VpnState.Connecting)
        homeRobot.swipeLeftToOpenMap()
        mapRobot.clickOnCountryNode(country)
                .clickConnectButtonWithoutVpnHandling()
                .clickCancelConnectionButton()
        mapRobot.verify {
                    isDisconnectedFromVpn()
                    isCountryNodeNotSelected(country)
                }
    }
}