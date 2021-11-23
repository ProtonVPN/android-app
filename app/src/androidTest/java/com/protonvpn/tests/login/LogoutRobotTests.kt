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

package com.protonvpn.tests.login

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.protonvpn.actions.ConnectionRobot
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.data.DefaultData
import com.protonvpn.actions.HomeRobot
import com.protonvpn.test.shared.TestUser.Companion.plusUser
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
 * [LogoutRobotTests] contains tests related to Logout process
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class LogoutRobotTests {
    private val testRule = ProtonHomeActivityTestRule()

    @get:Rule
    var rules = RuleChain
        .outerRule(HiltAndroidRule(this))
        .around(SetUserPreferencesRule(plusUser))
        .around(testRule)

    private lateinit var homeRobot: HomeRobot
    private lateinit var connectionRobot: ConnectionRobot

    @Before
    fun setup() {
        homeRobot = HomeRobot()
        connectionRobot = ConnectionRobot()
    }

    @Test
    fun successfulLogout() {
        homeRobot.logout()
            .verify { successfullyLoggedOut() }
    }

    @Test
    fun logoutWhileConnectedToVpn() {
        testRule.mockStatusOnConnect(VpnState.Connected)
        homeRobot.connectThroughQuickConnect(DefaultData.DEFAULT_CONNECTION_PROFILE)
            .verify { isConnected() }
        homeRobot.logout()
        homeRobot.verify {
                loginScreenIsNotDisplayed()
                warningMessageIsDisplayed()
            }
        homeRobot.logoutAfterWarning()
            .verify { successfullyLoggedOut() }
        connectionRobot.verify { isDisconnectedServiceHelper() }
    }

    @Test
    fun cancelLogoutWhileConnectedToVpn() {
        testRule.mockStatusOnConnect(VpnState.Connected)
        homeRobot.connectThroughQuickConnect(DefaultData.DEFAULT_CONNECTION_PROFILE)
            .verify { isConnected() }
        homeRobot.logout()
        homeRobot.verify {
                loginScreenIsNotDisplayed()
                warningMessageIsDisplayed()
            }
        homeRobot.cancelLogout()
        homeRobot.verify { loginScreenIsNotDisplayed() }
        connectionRobot.verify { isConnected() }
    }
}