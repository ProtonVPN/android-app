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
import androidx.test.filters.SdkSuppress
import com.protonvpn.actions.ProfilesRobot
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.data.DefaultData
import com.protonvpn.actions.ConnectionRobot
import com.protonvpn.actions.HomeRobot
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testRules.ProtonHomeActivityTestRule
import com.protonvpn.testRules.SetUserPreferencesRule
import com.protonvpn.testsHelper.ServiceTestHelper
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * [ConnectionRobot] contains tests related to how restrictions handle Secure Core
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class SecureCoreSecurityTests {

    private val testRule = ProtonHomeActivityTestRule()
    private val secureCoreServerDomain = "se-fr-01.protonvpn.com"

    @get:Rule
    var rules = RuleChain
        .outerRule(HiltAndroidRule(this))
        .around(SetUserPreferencesRule(TestUser.freeUser))
        .around(testRule)

    private lateinit var homeRobot: HomeRobot
    private lateinit var connectionRobot: ConnectionRobot
    private lateinit var serviceTestHelper: ServiceTestHelper
    private lateinit var profilesRobot: ProfilesRobot

    @Before
    fun setup() {
        homeRobot = HomeRobot()
        connectionRobot = ConnectionRobot()
        serviceTestHelper = ServiceTestHelper()
        profilesRobot = ProfilesRobot()
    }

    @Test
    fun tryToEnableSecureCoreAsFreeUser() {
        homeRobot.setStateOfSecureCoreSwitch(true)
            .verify {
                dialogUpgradeVisible()
                isSecureCoreDisabled()
            }
    }

    @Test
    fun tryToConnectToSecureCoreThroughProfilesAsFreeUser() {
        serviceTestHelper.addProfile(
            VpnProtocol.Smart,
            DefaultData.PROFILE_NAME,
            secureCoreServerDomain
        )
        homeRobot.swipeLeftToOpenProfiles()
        profilesRobot.clickOnUpgradeButton(DefaultData.PROFILE_NAME)
        homeRobot.verify { dialogUpgradeVisible() }
        connectionRobot.verify { isDisconnectedServiceHelper() }
    }

    @Test
    @SdkSuppress(minSdkVersion = 28)
    fun tryToConnectToSecureCoreThroughQuickConnectAsFreeUser() {
        val testProfile =
            serviceTestHelper.addProfile(
                VpnProtocol.Smart,
                DefaultData.PROFILE_NAME,
                secureCoreServerDomain
            )
        serviceTestHelper.setDefaultProfile(testProfile)
        homeRobot.connectThroughQuickConnect(DefaultData.PROFILE_NAME)
        homeRobot.verify { dialogUpgradeVisible() }
        connectionRobot.verify { isDisconnectedServiceHelper() }
    }
}