package com.protonvpn.tests.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.protonvpn.actions.SettingsRobot
import com.protonvpn.test.shared.TestUser
import com.protonvpn.tests.testRules.ProtonSettingsActivityTestRule
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
class SettingsRobotTest {

    private val settingsRobot = SettingsRobot()

    @get:Rule
    var rules = RuleChain
            .outerRule(HiltAndroidRule(this))
            .around(SetUserPreferencesRule(TestUser.getPlusUser()))
            .around(ProtonSettingsActivityTestRule())

    //TODO Check if scroll to can be simplified
    @Test
    fun checkIfSettingsViewIsVisible() {
        settingsRobot.verify {
            mainSettingsAreDisplayed()
        }
    }

    @Test
    fun selectFastestQuickConnection() {
        settingsRobot
                .setFastestQuickConnection()
                .verify {
                    quickConnectFastestProfileIsVisible()
                }
    }

    @Test
    fun setTooLowMTU() {
        settingsRobot
                .openMtuSettings()
                .setMTU(1279)
                .clickOnSaveMenuButton()
                .verify {
                    settingsMtuErrorIsShown()
                }
    }

    @Test
    fun setTooHighMTU() {
        settingsRobot
                .openMtuSettings()
                .setMTU(1501)
                .clickOnSaveMenuButton()
                .verify {
                    settingsMtuErrorIsShown()
                }
    }

    @Test
    fun switchSplitTunneling() {
        settingsRobot
                .toggleSplitTunneling()
                .verify {
                    splitTunnelIPIsVisible()
                }
        settingsRobot
                .toggleSplitTunneling()
                .verify {
                    splitTunnelIpIsNotVisible()
                }
    }
}