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

import android.view.View
import androidx.annotation.IdRes
import androidx.test.espresso.Espresso
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import com.protonvpn.android.R
import com.protonvpn.base.BaseRobot
import com.protonvpn.base.BaseVerify
import com.protonvpn.matchers.ProtonMatcher
import com.protonvpn.testsHelper.ConditionalActionsHelper
import me.proton.core.presentation.ui.view.ProtonAutoCompleteInput
import me.proton.core.presentation.ui.view.ProtonCheckbox
import me.proton.core.presentation.ui.view.ProtonInput
import org.hamcrest.Matcher

/**
 * [ProfilesRobot] Contains all actions and verifications for profiles screen
 */
class ProfilesRobot : BaseRobot() {

    private val serviceRobot = ServiceRobot()

    fun clickOnSaveButton(): ProfilesRobot = clickElementById(R.id.action_save)

    fun navigateBackFromForm(): ProfilesRobot = back()

    fun clickCancelButton(): ProfilesRobot = clickElementByText(R.string.cancel)

    fun clickDiscardButton(): ProfilesRobot = clickElementByText(R.string.discard)

    fun clickScSpeedInfoDialogActivate(): ProfilesRobot =
        clickElementByText(R.string.secureCoreActivateDialogButton)

    fun selectColorIndex(index: Int): ProfilesRobot =
        clickElementByIndexInParent(R.id.layoutPalette, index)

    fun clickOnConnectButton(profileName: String): ConnectionRobot =
        clickElementByIdAndContentDescription(R.id.buttonConnect, profileName)

    fun insertTextInProfileNameField(text: String): ProfilesRobot {
        scrollTo(R.id.inputName, ProtonInput::class.java)
        return replaceText(R.id.inputName, text)
    }

    fun selectFirstCountry(): ProfilesRobot {
        scrollToAndClickDropDown(R.id.inputCountry)
        return clickElementByText(serviceRobot.firstCountryFromBackend)
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

    fun selectRandomServer(): ProfilesRobot {
        scrollToAndClickDropDown(R.id.inputServer)
        return clickElementByText(R.string.profileRandom)
    }

    fun enableSecureCore(): ProfilesRobot {
        ConditionalActionsHelper().scrollDownInViewWithIdUntilObjectWithIdAppears(
            R.id.coordinator,
            R.id.checkboxSecureCore
        )
        return clickElement(R.id.checkboxSecureCore, ProtonCheckbox::class.java)
    }

    fun clickOnUpgradeButton(contentDescription: String): ConnectionRobot {
        view.waitForCondition {
            clickElementByIdAndContentDescription<Any>(R.id.buttonUpgrade, contentDescription)
            Espresso.onView(ViewMatchers.withText(R.string.upgrade_secure_core_title))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        }
        return ConnectionRobot()
    }

    fun clickOnConnectButtonUntilConnected(profileName: String): ConnectionRobot {
        view.waitForCondition {
            clickElementByIdAndContentDescription<Any>(R.id.buttonConnect, profileName)
            Espresso.onView(ViewMatchers.withId(R.id.buttonDisconnect))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        }
        return ConnectionRobot()
    }

    fun clickEditProfile(): ProfilesRobot {
        view
            .withVisibility(ViewMatchers.Visibility.VISIBLE)
            .withId(R.id.profile_edit_button)
            .click()
        return this
    }

    fun updateProfileName(newProfileName: String): ProfilesRobot =
        replaceText(R.id.inputName, newProfileName)

    fun clickDeleteProfile(): ProfilesRobot {
        ConditionalActionsHelper().scrollDownInViewWithIdUntilObjectWithIdAppears(
            R.id.coordinator,
            R.id.buttonDelete
        )
        clickElementById<ProfilesRobot>(R.id.buttonDelete)
        return clickElementByText(R.string.delete)
    }

    fun clickOnCreateNewProfileButton(): ProfilesRobot {
        clickOnCreateNewProfileUntilMatcherIsDisplayed(ViewMatchers.withId(R.id.action_save))
        return this
    }

    fun clickOnCreateNewProfileUntilUpsellIsShown() : UpsellModalRobot {
        clickOnCreateNewProfileUntilMatcherIsDisplayed(ProtonMatcher.withHtmlText(R.string.upgrade_profiles_text))
        return UpsellModalRobot()
    }

    fun selectOpenVPNProtocol(udp: Boolean): ProfilesRobot {
        scrollToAndClickDropDown(R.id.inputProtocol)
        val protocol =
            if (udp) R.string.settingsProtocolNameOpenVpnUdp
            else R.string.settingsProtocolNameOpenVpnTcp
        return clickElementByText(protocol)
    }

    private fun clickOnCreateNewProfileUntilMatcherIsDisplayed(matcher: Matcher<View>) {
        waitUntilDisplayedByText<Any>(R.string.create_new_profile)
        view.waitForCondition {
            clickElementByText<Any>(R.string.create_new_profile)
            Espresso.onView(matcher)
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        }
    }

    private fun scrollToAndClickDropDown(@IdRes id: Int): ProfilesRobot {
        ConditionalActionsHelper().scrollDownInViewWithIdUntilObjectWithIdAppears(
            R.id.coordinator,
            R.id.inputProtocol,
            ProtonAutoCompleteInput::class.java
        )
        return clickElement(id, ProtonAutoCompleteInput::class.java)
    }

    private fun scrollTo(@IdRes id: Int, clazz: Class<out Any>): ProfilesRobot {
        ConditionalActionsHelper().scrollDownInViewWithIdUntilObjectWithIdAppears(
            R.id.coordinator,
            id,
            clazz
        )
        return this
    }

    class Verify : BaseVerify() {

        fun errorEmptyNameIsVisible() = checkIfElementIsDisplayedByStringId(R.string.errorEmptyName)

        fun errorEmptyCountryIsVisible() =
            checkIfElementIsDisplayedByStringId(R.string.errorEmptyCountry)

        fun upgradeButtonIsDisplayed() = checkIfElementIsDisplayedById(R.id.buttonUpgrade)

        fun defaultProfileOptionsAreVisible() {
            checkIfElementIsDisplayedByStringId(R.string.profileFastest)
            checkIfElementIsDisplayedByStringId(R.string.profileRandom)
        }

        fun discardChangesDialogIsVisible() =
            checkIfElementIsDisplayedByStringId(R.string.discardChanges)

        fun profileIsNotVisible(profileName: String) {
            view.withId(R.id.textServer).withText(profileName).checkDoesNotExist()
        }

        fun profileIsVisible(profileName: String) {
            view.withId(R.id.textServer).withText(profileName).checkDisplayed()
        }

        fun defaultProfilesArePaid() {
            view.withId(R.id.buttonUpgrade).withContentDesc(R.string.profileFastest).checkDisplayed()
            view.withId(R.id.buttonUpgrade).withContentDesc(R.string.profileRandom).checkDisplayed()
        }
    }

    inline fun verify(block: Verify.() -> Unit) = Verify().apply(block)
}
