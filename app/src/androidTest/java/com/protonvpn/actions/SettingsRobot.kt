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
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.platform.app.InstrumentationRegistry
import com.protonvpn.android.R
import com.protonvpn.android.ui.settings.SettingsItem
import com.protonvpn.android.utils.HtmlTools
import com.protonvpn.base.BaseRobot
import com.protonvpn.base.BaseVerify
import com.protonvpn.testsHelper.ConditionalActionsHelper

/**
 * [SettingsRobot] Contains all actions for settings component
 */
class SettingsRobot : BaseRobot() {

    fun setRandomQuickConnection(): SettingsRobot = setQuickConnection(R.string.profileRandom)

    fun clickOnSaveMenuButton(): SettingsRobot = clickElementById(R.id.action_save)

    fun clickOnAlwaysOnVpnSetting(): SettingsRobot = clickElementById(R.id.buttonAlwaysOn)

    fun pressOpenAndroidSettings(): SettingsRobot = clickElementById(R.id.buttonOpenVpnSettings)

    fun openMtuSettings(): SettingsRobot {
        ConditionalActionsHelper().scrollDownInViewWithIdUntilObjectWithIdAppears(
            R.id.scrollView,
            R.id.buttonMtuSize
        )
        return clickElement(R.id.buttonMtuSize, SettingsItem::class.java)
    }

    fun setMTU(mtu: Int): SettingsRobot =
        replaceText(R.id.inputMtu, mtu.toString())

    fun toggleSplitTunneling(): SettingsRobot {
        ConditionalActionsHelper().scrollDownInViewWithIdUntilObjectWithTextAppears(
            R.id.scrollView,
            R.string.settingsSplitTunnelingTitle
        )
        return clickElementByText(R.string.settingsSplitTunnelingTitle)
    }

    private fun setQuickConnection(@StringRes profileName: Int): SettingsRobot {
        clickElement<SettingsRobot>(R.id.buttonDefaultProfile, SettingsItem::class.java)
        return clickElementByText(profileName)
    }

    class Verify : BaseVerify() {

        fun settingsMtuErrorIsShown() =
            checkIfElementIsDisplayedByStringId(R.string.settingsMtuRangeInvalid)

        fun mtuSizeMatches(mtuSize: String) {
            view.withId(R.id.textValue).withParent(view.withId(R.id.buttonMtuSize))
                .checkContains(mtuSize)
        }

        fun splitTunnelUIIsVisible() {
            checkIfElementIsDisplayedById(R.id.buttonExcludeIps)
            checkIfElementIsDisplayedById(R.id.buttonExcludeApps)
        }

        fun splitTunnelUIIsNotVisible() {
            checkIfElementIsNotDisplayedById(R.id.buttonExcludeIps)
            checkIfElementIsNotDisplayedById(R.id.buttonExcludeApps)
        }

        fun quickConnectRandomProfileIsVisible() =
            checkIfElementIsDisplayedByStringId(R.string.profileRandom)

        fun mainSettingsAreDisplayed() {
            checkIfElementIsDisplayedById(R.id.textSectionQuickConnect)
            ConditionalActionsHelper().scrollDownInViewWithIdUntilObjectWithIdAppears(
                R.id.scrollView,
                R.id.buttonProtocol
            )
            ConditionalActionsHelper().scrollDownInViewWithIdUntilObjectWithIdAppears(
                R.id.scrollView,
                R.id.switchShowSplitTunnel
            )
        }

        fun openVpnSettingsNavigatesToSettings() {
            Intents.intended(IntentMatchers.toPackage("com.android.settings"))
        }

        fun alwaysOnOnboardingFlowIsCorrect() {
            checkIfElementIsDisplayedByText(getCaption(R.string.settingsAlwaysOnWindowStep1).toString())
            clickElementById<SettingsRobot>(R.id.buttonNext)
            checkIfElementIsDisplayedByStringId(R.string.settingsAlwaysOnWindowStep2)
            clickElementById<SettingsRobot>(R.id.buttonNext)
            checkIfElementIsDisplayedByText(getCaption(R.string.settingsAlwaysOnWindowStep3).toString())
            clickElementById<SettingsRobot>(R.id.buttonNext)
            checkIfElementIsDisplayedByText(getCaption(R.string.settingsAlwaysOnWindowStep4).toString())
        }

        private fun getCaption(@StringRes text: Int): CharSequence = HtmlTools.fromHtml(
            InstrumentationRegistry.getInstrumentation().targetContext.getString((text))
        )
    }

    inline fun verify(block: Verify.() -> Unit) = Verify().apply(block)
}
