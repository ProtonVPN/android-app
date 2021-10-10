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

import androidx.annotation.StringRes
import com.protonvpn.android.R
import com.protonvpn.android.ui.settings.SettingsItem
import com.protonvpn.base.BaseRobot
import com.protonvpn.base.BaseVerify
import com.protonvpn.results.SettingsResults
import com.protonvpn.tests.upgrade.UpgradeTestData
import com.protonvpn.testsHelper.ConditionalActionsHelper
import com.protonvpn.testsHelper.scrollToEx

class SettingsRobot : BaseRobot() {
    fun navigateBackToHomeScreen(): SettingsRobot = clickElementByContentDescription("Navigate up")

    fun setFastestQuickConnection(): SettingsRobot = setQuickConnection(R.string.profileFastest)

    fun setRandomQuickConnection(): SettingsRobot = setQuickConnection(R.string.profileRandom)

    fun openExcludedIPAddressesList(): SettingsRobot =
            clickElementByContentDescription("Exclude IP addresses")

    fun clickOnDoneButton(): SettingsRobot = clickElementByText("DONE")

    fun clickOnSaveMenuButton(): SettingsRobot = clickElementById(R.id.action_save)

    fun openMtuSettings(): SettingsRobot {
        ConditionalActionsHelper.scrollDownInViewWithIdUntilObjectWithIdAppears(
                R.id.scrollView,
                R.id.buttonMtuSize
        )
        clickElement<SettingsRobot>(R.id.buttonMtuSize, SettingsItem::class.java)
        return this
    }

    fun setMTU(mtu: Int): SettingsRobot {
        clearText<SettingsResults>(R.id.inputMtu)
        setText<SettingsRobot>(R.id.inputMtu, mtu.toString())
        return this
    }

    fun toggleSplitTunneling(): SettingsRobot {
        ConditionalActionsHelper.scrollDownInViewWithIdUntilObjectWithTextAppears(
                R.id.scrollView,
                "Split tunneling allows certain apps or IPs to be excluded from the VPN traffic."
        )
        clickElementByText<SettingsRobot>(R.string.settingsSplitTunnelingTitle)
        return this
    }

    fun addIpAddressInSplitTunneling(): SettingsRobot {
        openExcludedIPAddressesList()
        view
            .withContentDesc("Add IP Address")
            .typeText(UpgradeTestData.excludedIPAddress)
        clickElementByText<SettingsRobot>("ADD")
        clickOnDoneButton()
        return this
    }

    private fun setQuickConnection(@StringRes profileName: Int): SettingsRobot {
        clickElement<SettingsRobot>(R.id.buttonDefaultProfile, SettingsItem::class.java)
        clickElementByText<SettingsRobot>(profileName)
        return this
    }

    class Verify : BaseVerify(){

        fun settingsMtuErrorIsShown(): Verify =
                checkIfElementIsDisplayedByStringId(R.string.settingsMtuRangeInvalid)

        fun splitTunnelIPIsVisible(): SettingsResults =
                checkIfElementIsDisplayedById(R.id.buttonExcludeIps)

        fun splitTunnelIpIsNotVisible() : SettingsResults =
                checkIfElementIsNotDisplayedById(R.id.buttonExcludeIps)

        fun quickConnectFastestProfileIsVisible() : SettingsResults =
                checkIfElementIsDisplayedByStringId(R.string.profileFastest)

        fun mainSettingsAreDisplayed(){
            checkIfElementIsDisplayedById<Verify>(R.id.textSectionQuickConnect)
            ConditionalActionsHelper.scrollDownInViewWithIdUntilObjectWithIdAppears(R.id.scrollView,
                    R.id.buttonProtocol)
            ConditionalActionsHelper.scrollDownInViewWithIdUntilObjectWithIdAppears(R.id.scrollView,
                    R.id.switchShowSplitTunnel)
        }
    }

    inline fun verify(block: Verify.() -> Unit) = Verify().apply(block)
}
