/*
 * Copyright (c) 2018 Proton Technologies AG
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
package com.protonvpn.tests.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import com.protonvpn.actions.SettingsRobot
import com.protonvpn.annotations.TestID
import com.protonvpn.mocks.TestApiConfig
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testRules.ProtonHiltAndroidRule
import com.protonvpn.testRules.ProtonSettingsActivityTestRule
import com.protonvpn.testRules.SetLoggedInUserRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * [SettingsTests] Contains UI tests for Settings
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class SettingsTests {

    private val settingsRobot = SettingsRobot()

    @get:Rule
    var rules = RuleChain
        .outerRule(ProtonHiltAndroidRule(this, TestApiConfig.Mocked(TestUser.plusUser)))
        .around(SetLoggedInUserRule(TestUser.plusUser))
        .around(ProtonSettingsActivityTestRule())

    @Test
    @TestID(103967)
    fun checkIfSettingsViewIsVisible() {
        settingsRobot.verify { mainSettingsAreDisplayed() }
    }

    @Test
    @TestID(103969)
    fun selectRandomQuickConnection() {
        settingsRobot.setRandomQuickConnection()
            .verify { quickConnectRandomProfileIsVisible() }
    }

    @Test
    @TestID(91)
    fun setTooLowMTU() {
        settingsRobot.openMtuSettings()
            .setMTU(1279)
            .clickOnSaveMenuButton()
            .verify { settingsMtuErrorIsShown() }
    }

    @Test
    @TestID(103968)
    fun setTooHighMTU() {
        settingsRobot.openMtuSettings()
            .setMTU(1501)
            .clickOnSaveMenuButton()
            .verify { settingsMtuErrorIsShown() }
    }

    @Test
    @TestID(121737)
    fun setValidMTU() {
        settingsRobot.openMtuSettings()
            .setMTU(1500)
            .clickOnSaveMenuButton()
            .verify { mtuSizeMatches("1500") }
    }

    @Test
    @TestID(103970)
    fun switchSplitTunneling() {
        settingsRobot.toggleSplitTunneling()
            .verify { splitTunnelUIIsVisible() }
        Thread.sleep(300) // Tapping the switch again too soon doesn't toggle it.
        settingsRobot.toggleSplitTunneling()
            .verify { splitTunnelUIIsNotVisible() }
    }

    @Test
    @TestID(121427)
    @SdkSuppress(minSdkVersion = 28)
    fun alwaysOnNavigatesToSettings() {
        settingsRobot.clickOnAlwaysOnVpnSetting()
            .pressOpenAndroidSettings()
            .verify { openVpnSettingsNavigatesToSettings() }
    }

    @Test
    @TestID(121428)
    @SdkSuppress(minSdkVersion = 28)
    fun alwaysOnOnboarding() {
        settingsRobot.clickOnAlwaysOnVpnSetting()
            .pressOpenAndroidSettings()
            .verify { alwaysOnOnboardingFlowIsCorrect() }
    }
}
