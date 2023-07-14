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
import com.protonvpn.actions.compose.ConnectionRobot
import com.protonvpn.actions.compose.HomeRobot
import com.protonvpn.actions.compose.interfaces.verify
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.testRules.CommonRuleChains
import com.protonvpn.testRules.CommonRuleChains.mockedLoggedInRule
import com.protonvpn.testsHelper.ServiceTestHelper
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [LogoutTests] contains tests related to Logout process
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class LogoutTests {

    @get:Rule
    val rule = mockedLoggedInRule()

    @Before
    fun setup() {
        ServiceTestHelper().mockVpnBackend.stateOnConnect = VpnState.Connected
    }

    @Test
    fun successfulLogout() {
        HomeRobot.logout()
            .verify { addAccountElementsDisplayed() }
    }

    @Test
    fun cancelLogoutWhileConnectedToVpn() {
        ConnectionRobot.quickConnect()
            .verify { isConnected() }
        HomeRobot.logout()
        HomeRobot.verify { signOutWarningMessageIsDisplayed() }
        HomeRobot.cancelLogout()
            .verify { isLoggedIn() }
        HomeRobot.navigateToHome()
        ConnectionRobot.verify { isConnected() }
    }

    @Test
    fun logoutWhileConnectedToVpn() {
        ConnectionRobot.quickConnect()
            .verify { isConnected() }
        HomeRobot.logout()
        HomeRobot.verify { signOutWarningMessageIsDisplayed() }
        HomeRobot.confirmLogout()
            .verify { addAccountElementsDisplayed() }
    }
}
