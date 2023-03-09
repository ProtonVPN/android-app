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
import com.protonvpn.actions.HomeRobot
import com.protonvpn.android.ui.main.MobileMainActivity
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.annotations.TestID
import com.protonvpn.data.DefaultData
import com.protonvpn.mocks.TestApiConfig
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testRules.LoggedInActivityTestRule
import com.protonvpn.testRules.ProtonHiltAndroidRule
import com.protonvpn.testRules.SetLoggedInUserRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * [LogoutTests] contains tests related to Logout process
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class LogoutTests {
    // Start via the MobileMainActivity so that logging out can navigate back to it.
    private val testRule = LoggedInActivityTestRule(MobileMainActivity::class.java)

    @get:Rule
    var rules = RuleChain
        .outerRule(ProtonHiltAndroidRule(this, TestApiConfig.Mocked(TestUser.plusUser)))
        .around(SetLoggedInUserRule(TestUser.plusUser))
        .around(testRule)

    private lateinit var homeRobot: HomeRobot
    private lateinit var connectionRobot: ConnectionRobot

    @Before
    fun setup() {
        homeRobot = HomeRobot()
        connectionRobot = ConnectionRobot()
    }

    @Test
    @TestID(54)
    fun successfulLogout() {
        homeRobot.logout()
            .verify { addAccountElementsDisplayed() }
    }

    @Test
    @TestID(55)
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
            .verify { addAccountElementsDisplayed() }
        connectionRobot.verify { isDisconnectedServiceHelper() }
    }

    @Test
    @TestID(103965)
    fun cancelLogoutWhileConnectedToVpn() {
        testRule.mockStatusOnConnect(VpnState.Connected)
        homeRobot.connectThroughQuickConnect(DefaultData.DEFAULT_CONNECTION_PROFILE)
            .verify { isConnected() }
        homeRobot.logout()
        homeRobot.verify {
                loginScreenIsNotDisplayed()
                warningMessageIsDisplayed()
            }
        homeRobot.clickCancel()
        homeRobot.verify { loginScreenIsNotDisplayed() }
        connectionRobot.verify { isConnected() }
    }
}
