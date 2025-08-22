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

package com.protonvpn.tests.login.mobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.robots.mobile.ConnectionRobot
import com.protonvpn.robots.mobile.HomeRobot
import com.protonvpn.interfaces.verify
import com.protonvpn.testRules.CommonRuleChains.mockedLoggedInRule
import com.protonvpn.testsHelper.ServerManagerHelper
import dagger.hilt.android.testing.HiltAndroidTest
import me.proton.test.fusion.Fusion
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import me.proton.core.auth.presentation.R as AuthR

/**
 * [LogoutTestsMocked] contains tests related to Logout process
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class LogoutTestsMocked {

    @get:Rule
    val rule = mockedLoggedInRule()

    @Before
    fun setup() {
        ServerManagerHelper().backend.stateOnConnect = VpnState.Connected
    }

    @Test
    fun successfulLogout() {
        HomeRobot.logout()
            .verify { credentiallessWelcomeScreenDisplayed() }
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
            .verify { credentiallessWelcomeScreenDisplayed() }
    }

    // TODO: add verify functionality to CredentialLessWelcomeRobot.
    private fun credentiallessWelcomeScreenDisplayed() =
        Fusion.view.withId(AuthR.id.sign_in_guest).checkIsDisplayed()
}
