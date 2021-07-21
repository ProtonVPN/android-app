/*
 * Copyright (c) 2019 Proton Technologies AG
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
package com.protonvpn.actions

import com.protonvpn.android.R
import com.protonvpn.results.SettingsResults
import com.protonvpn.tests.upgrade.UpgradeTestData
import com.protonvpn.testsHelper.ConditionalActionsHelper
import com.protonvpn.testsHelper.UIActionsTestHelper

class SettingsRobot : UIActionsTestHelper() {
    fun navigateBackToHomeScreen(): SettingsRobot {
        clickOnObjectWithContentDescription("Navigate up")
        waitUntilObjectWithTextAppearsInView("PROFILES")
        return this
    }

    fun setFastestQuickConnection(): SettingsRobot {
        clickOnObjectWithId(R.id.buttonDefaultProfile)
        clickOnObjectWithText("Fastest")
        checkIfObjectWithTextIsDisplayed("Fastest")
        return this
    }

    fun setRandomQuickConnection(): SettingsRobot {
        clickOnObjectWithId(R.id.buttonDefaultProfile)
        clickOnObjectWithText("Random")
        checkIfObjectWithTextIsDisplayed("Random")
        return this
    }

    fun setMTU(mtu: Int): SettingsResults {
        ConditionalActionsHelper.scrollDownInViewWithIdUntilObjectWithTextAppears(
            R.id.scrollView,
            R.string.settingsMtuDescription
        )
        insertTextIntoFieldWithId(R.id.textMTU, mtu.toString())
        return SettingsResults()
    }

    fun toggleSplitTunneling(): SettingsResults {
        ConditionalActionsHelper.scrollDownInViewWithIdUntilObjectWithTextAppears(
            R.id.scrollView,
            "Split tunneling allows certain apps or IPs to be excluded from the VPN traffic."
        )
        clickOnObjectWithText(R.string.settingsSplitTunnelingTitle)
        return SettingsResults()
    }

    fun openExcludedIPAddressesList(): SettingsRobot {
        clickOnObjectWithContentDescription("Exclude IP addresses")
        return this
    }

    fun clickOnDoneButton(): SettingsRobot {
        clickOnObjectWithText("DONE")
        return this
    }

    fun addIpAddressInSplitTunneling(): SettingsRobot {
        openExcludedIPAddressesList()
        insertTextIntoFieldWithContentDescription(
            "Add IP Address",
            UpgradeTestData.excludedIPAddress
        )
        clickOnObjectWithText("ADD")
        clickOnDoneButton()
        return this
    }
}
