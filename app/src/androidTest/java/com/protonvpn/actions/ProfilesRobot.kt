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
package com.protonvpn.actions

import androidx.annotation.IdRes
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.matcher.ViewMatchers
import com.protonvpn.MockSwitch
import com.protonvpn.android.ProtonApplication
import com.protonvpn.android.R
import com.protonvpn.base.BaseRobot
import com.protonvpn.results.ConnectionResult
import com.protonvpn.results.ProfilesResult
import com.protonvpn.testsHelper.ConditionalActionsHelper
import me.proton.core.presentation.ui.view.ProtonAutoCompleteInput
import me.proton.core.presentation.ui.view.ProtonInput

class ProfilesRobot : BaseRobot() {
    private val serviceRobot = ServiceRobot()
    val profilesResult = ProfilesResult()

    fun selectColorIndex(index: Int): ProfilesRobot =
        clickElementByIndexInParent(R.id.layoutPalette, index)

    fun clickOnFastestOption(): ProfilesRobot = clickElementByText(R.string.profileFastest)

    fun clickOnConnectButton(profileName: String): ConnectionResult {
        clickElementByIdAndContentDescription<ProfilesRobot>(R.id.buttonConnect, profileName)
        if (!MockSwitch.mockedConnectionUsed) {
            HomeRobot().allowToUseVpn()
        }
        return ConnectionResult()
    }

    fun clickOnCreateNewProfileButton(): ProfilesRobot =
        clickElementByText(R.string.create_new_profile)

    fun insertTextInProfileNameField(text: String): ProfilesRobot {
        scrollTo(R.id.inputName, ProtonInput::class.java)
        return replaceText(R.id.inputName, text)
    }

    fun selectFirstCountry(): ProfilesRobot {
        scrollToAndClickDropDown(R.id.inputCountry)
        return clickElementByText(serviceRobot.firstCountryFromBackend)
    }

    fun selectFirstSecureCoreExitCountry(): ProfilesRobot {
        scrollToAndClickDropDown(R.id.inputCountry)
        return clickElementByText(serviceRobot.firstSecureCoreExitCountryFromBackend.countryName)
    }

    fun selectSecondSecureCoreExitCountry(): ProfilesRobot {
        scrollToAndClickDropDown(R.id.inputCountry)
        return clickElementByText(serviceRobot.secondSecureCoreExitCountryFromBackend.countryName)
    }

    fun selectSecureCoreEntryCountryForSecondExit(): ProfilesRobot {
        val exitCountry = serviceRobot.secondSecureCoreExitCountryFromBackend
        scrollToAndClickDropDown(R.id.inputServer)
        return clickElementByText(serviceRobot.getSecureCoreEntryCountryFromBackend(exitCountry))
    }

    fun selectFirstNotAccessibleVpnCountry(): ProfilesRobot {
        scrollToAndClickDropDown(R.id.inputCountry)
        val context = ApplicationProvider.getApplicationContext<ProtonApplication>()
        return clickElementByText(
            context.getString(
                R.string.serverLabelUpgrade,
                serviceRobot.firstNotAccessibleVpnCountryFromBackend
            )
        )
    }

    fun selectRandomServer(): ProfilesRobot {
        scrollToAndClickDropDown(R.id.inputServer)
        // TODO Use "Random" instead of Fastest once random profile problems are solved
        return clickElementByText(R.string.profileFastest)
    }

    fun selectNonAccessibleRandomServer(): ProfilesResult {
        clickElement<ProfilesRobot>(R.id.inputServer, ProtonAutoCompleteInput::class.java)
        val context = ApplicationProvider.getApplicationContext<ProtonApplication>()
        return clickElementByText(
            context.getString(R.string.serverLabelUpgrade, context.getString(R.string.profileRandom))
        )
    }

    fun clickOnSaveButton(): ProfilesResult = clickElementById(R.id.action_save)

    fun selectProfile(profileName: String): ProfilesRobot = clickElementByText(profileName)

    fun navigateBackFromForm(): ProfilesResult = back()

    fun clickCancelButton(): ProfilesResult = clickElementByText(R.string.cancel)

    fun clickDiscardButton(): ProfilesResult = clickElementByText(R.string.discard)

    fun enableSecureCore(): ProfilesRobot {
        ConditionalActionsHelper.scrollDownInViewWithIdUntilObjectWithTextAppears(
            R.id.coordinator,
            R.string.secure_core
        )
        return clickElementByText(R.string.secure_core)
    }

    fun clickYesButton(): ConnectionResult =  clickElementById(R.id.md_buttonDefaultPositive)

    fun clickOnUpgradeButton(contentDescription: String): ConnectionResult =
        clickElementByIdAndContentDescription(R.id.buttonUpgrade, contentDescription)

    fun clickEditProfile(): ProfilesRobot {
        view
            .withVisibility(ViewMatchers.Visibility.VISIBLE)
            .withId(R.id.profile_edit_button)
            .click()
        return this
    }

    fun updateProfileName(newProfileName: String): ProfilesRobot {
        clearText<ProfilesRobot>(R.id.inputName)
        return setText(R.id.inputName, newProfileName)
    }

    fun clickDeleteProfile(): ProfilesRobot {
        ConditionalActionsHelper.scrollDownInViewWithIdUntilObjectWithIdAppears(
            R.id.coordinator,
            R.id.buttonDelete
        )
        clickElementById<ProfilesRobot>(R.id.buttonDelete)
        return clickElementByText(R.string.delete)
    }

    fun selectOpenVPNProtocol(udp: Boolean): ProfilesRobot {
        scrollToAndClickDropDown(R.id.inputProtocol)
        val protocol =
            if (udp) R.string.settingsProtocolNameOpenVpnUdp
            else R.string.settingsProtocolNameOpenVpnTcp
        return clickElementByText(protocol)
    }

    private fun scrollToAndClickDropDown(@IdRes id: Int): ProfilesRobot {
        ConditionalActionsHelper.scrollDownInViewWithIdUntilObjectWithIdAppears(
            R.id.coordinator,
            R.id.inputProtocol,
            ProtonAutoCompleteInput::class.java
        )
        return clickElement(id, ProtonAutoCompleteInput::class.java)
    }

    private fun scrollTo(@IdRes id: Int, clazz: Class<out Any>): ProfilesRobot {
        ConditionalActionsHelper.scrollDownInViewWithIdUntilObjectWithIdAppears(
            R.id.coordinator,
            id,
            clazz
        )
        return this
    }
}
