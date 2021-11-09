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
import com.protonvpn.testsHelper.ConditionalActionsHelper

/**
 * [SettingsRobot] Contains all actions for settings component
 */
class SettingsRobot : BaseRobot() {

    fun setFastestQuickConnection(): SettingsRobot = setQuickConnection(R.string.profileFastest)

    fun clickOnSaveMenuButton(): SettingsRobot = clickElementById(R.id.action_save)

    fun openMtuSettings(): SettingsRobot {
        ConditionalActionsHelper.scrollDownInViewWithIdUntilObjectWithIdAppears(
            R.id.scrollView,
            R.id.buttonMtuSize
        )
        return clickElement(R.id.buttonMtuSize, SettingsItem::class.java)
    }

    fun setMTU(mtu: Int): SettingsRobot =
        replaceText(R.id.inputMtu, mtu.toString())

    fun toggleSplitTunneling(): SettingsRobot {
        ConditionalActionsHelper.scrollDownInViewWithIdUntilObjectWithTextAppears(
            R.id.scrollView,
            R.string.settingsSplitTunnelingDescription
        )
        return clickElementByText(R.string.settingsSplitTunnelingTitle)
    }

    private fun setQuickConnection(@StringRes profileName: Int): SettingsRobot {
        clickElement<SettingsRobot>(R.id.buttonDefaultProfile, SettingsItem::class.java)
        return clickElementByText(profileName)
    }

    class Verify : BaseVerify(){

        fun settingsMtuErrorIsShown() = checkIfElementIsDisplayedByStringId(R.string.settingsMtuRangeInvalid)

        fun splitTunnelIPIsVisible() = checkIfElementIsDisplayedById(R.id.buttonExcludeIps)

        fun splitTunnelIpIsNotVisible() = checkIfElementIsNotDisplayedById(R.id.buttonExcludeIps)

        fun quickConnectFastestProfileIsVisible() = checkIfElementIsDisplayedByStringId(R.string.profileFastest)

        fun mainSettingsAreDisplayed(){
            checkIfElementIsDisplayedById(R.id.textSectionQuickConnect)
            ConditionalActionsHelper.scrollDownInViewWithIdUntilObjectWithIdAppears(R.id.scrollView,
                    R.id.buttonProtocol)
            ConditionalActionsHelper.scrollDownInViewWithIdUntilObjectWithIdAppears(R.id.scrollView,
                    R.id.switchShowSplitTunnel)
        }
    }

    inline fun verify(block: Verify.() -> Unit) = Verify().apply(block)
}
