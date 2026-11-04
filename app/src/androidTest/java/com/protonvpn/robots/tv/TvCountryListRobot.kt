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

package com.protonvpn.robots.tv

import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isSelected
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.protonvpn.android.R
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.base.BaseRobot
import com.protonvpn.data.Timeouts
import me.proton.test.fusion.Fusion.node
import me.proton.test.fusion.FusionConfig

/**
 * [TvCountryListRobot] Contains all actions and verifications for home view
 *
 * Supports both the legacy and the redesigned main screen, depending on the TvMainScreenRedesign feature flag.
 */
class TvCountryListRobot : BaseRobot() {

    private val uiDevice: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    fun connectToRecommendedCountry(): TvCountryListRobot =
        if (isTvMainScreenRedesign()) {
            clickNode(R.string.tv_vpn_status_action_connect_fastest)
        } else {
            clickElementByText(R.string.tv_quick_connect_recommened)
        }

    fun disconnectFromCountry(): TvCountryListRobot =
        if (isTvMainScreenRedesign()) {
            clickNode(R.string.disconnect)
        } else {
            clickElementByText(R.string.disconnect)
        }

    fun confirmSignOut(): TvLoginRobot =
        if (isTvMainScreenRedesign()) {
            clickDialogNode(R.string.tv_signout_dialog_ok)
            TvLoginRobot()
        } else {
            clickDialogElementByText(R.string.dialog_sign_out_action)
        }

    fun cancelSignOut(): TvCountryListRobot =
        if (isTvMainScreenRedesign()) {
            clickDialogNode(R.string.cancel)
        } else {
            clickDialogElementByText(R.string.cancel)
        }

    fun navigateToHome(): TvCountryListRobot {
        if (isTvMainScreenRedesign()) {
            // Back moves focus to the drawer which then expands and shows item labels.
            uiDevice.pressBack()
            clickNode(R.string.bottom_nav_home)
            waitForVpnStatusAction()
        }
        return this
    }

    fun getConnectionStatus(): String =
        if (isTvMainScreenRedesign()) {
            getVpnStatusTitle()
        } else {
            getText(onView(withId(R.id.textStatus)))
        }

    fun waitUntilCountryIsLoaded(countryCode: String): TvCountryListRobot {
        if (isTvMainScreenRedesign()) {
            countryTile(countryCode).await { assertExists() }
        } else {
            view.withText(countryCode).isCompletelyDisplayed()
        }
        return this
    }

    fun connectToFavouriteCountry() : TvCountryListRobot {
        pressFavourite()
        return TvCountryListRobot()
    }

    fun openFirstCountryConnectionWindow() : TvDetailedCountryRobot {
        waitUntilDisplayedByText<Any>(R.string.tv_quick_connect_recommened)
        uiDevice.pressDPadDown()
        uiDevice.pressDPadCenter()
        return waitUntilDisplayed(R.id.countryDescription)
    }

    fun connectToCountry(countryCode: String): TvCountryListRobot {
        countryTile(countryCode).await { assertIsDisplayed() }
        countryTile(countryCode).interaction.performSemanticsAction(SemanticsActions.OnClick)
        return this
    }

    fun connectToCity(countryCode: String, city: String): TvCountryListRobot {
        countryTile(countryCode).await { assertIsDisplayed() }
        countryTile(countryCode).interaction.performSemanticsAction(SemanticsActions.OnLongClick)
        node.withText(city).await { assertIsDisplayed() }
        node.withText(city).interaction.performSemanticsAction(SemanticsActions.OnClick)
        return this
    }

    fun signOut(): TvCountryListRobot {
        if (isTvMainScreenRedesign()) {
            signOutRedesign()
        } else {
            signOutLegacy()
        }
        return this
    }

    private fun signOutRedesign() {
        // The VPN status action button is focused on start, moving left from it focuses the drawer which then
        // expands and shows item labels.
        waitForVpnStatusAction()
        uiDevice.pressDPadLeft()
        clickNode(R.string.botton_nav_settings)
        node.withText(R.string.tv_signout_label).await { assertExists() }
        node.withText(R.string.tv_signout_label).scrollTo()
        node.withText(R.string.tv_signout_label).interaction.performSemanticsAction(SemanticsActions.OnClick)
    }

    private fun signOutLegacy() {
        view.waitForCondition(watchTimeout = Timeouts.TWENTY_SECONDS_MS) {
            uiDevice.pressDPadDown()
            onView(withText(R.string.tv_row_settings)).check(matches(isDisplayed()))
        }
        uiDevice.pressDPadDown()

        // Using Compose test rule in tests using this robot makes these checks slow (~5s per loop),
        // to be investigated.
        view.waitForCondition(watchTimeout = Timeouts.ONE_MINUTE_MS) {
            uiDevice.pressDPadRight()
            onView(withText(R.string.tv_signout_label)).check(matches(isSelected()))
        }
        uiDevice.pressDPadCenter()
    }

    private fun pressFavourite() : TvCountryListRobot = clickElementByText(R.string.tv_quick_connect_favourite)

    private fun clickNode(@StringRes textRes: Int): TvCountryListRobot {
        node.withText(textRes).await { assertIsDisplayed() }
        node.withText(textRes).interaction.performSemanticsAction(SemanticsActions.OnClick)
        return this
    }

    private fun waitForVpnStatusAction() {
        val composeRule = FusionConfig.Compose.testRule.get()
        val statusActions = listOf(R.string.tv_vpn_status_action_connect_fastest, R.string.disconnect)
            .map { getString(it) }
        composeRule.waitUntil(Timeouts.TWENTY_SECONDS_MS) {
            statusActions.any { composeRule.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() }
        }
    }

    private fun clickDialogNode(@StringRes textRes: Int): TvCountryListRobot {
        val composeRule = FusionConfig.Compose.testRule.get()
        val matcher = hasText(getString(textRes)) and hasAnyAncestor(isDialog())
        composeRule.waitUntil(Timeouts.TWENTY_SECONDS_MS) {
            composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(matcher).performSemanticsAction(SemanticsActions.OnClick)
        return this
    }

    private fun getVpnStatusTitle(): String {
        val composeRule = FusionConfig.Compose.testRule.get()
        val statusTitles = listOf(
            R.string.tv_vpn_status_unprotected,
            R.string.tv_vpn_status_connecting,
            R.string.tv_vpn_status_protected,
        ).map { getString(it) }
        return statusTitles.first { title ->
            composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun getString(@StringRes textRes: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(textRes)

    class Verify : ConnectionVerify() {
        fun countryTileIsConnected(countryCode: String) {
            countryTile(countryCode).withText(R.string.tv_country_tile_connected).await { assertIsDisplayed() }
        }

        fun userIsConnectedToCity(countryCode: String, city: String) {
            node.withTextSubstring("${CountryTools.getFullName(countryCode)} - $city").await { assertIsDisplayed() }
        }

        fun userIsLoggedIn() {
            if (isTvMainScreenRedesign()) {
                node.withText(R.string.tv_vpn_status_action_connect_fastest).await { assertIsDisplayed() }
            } else {
                checkIfElementIsDisplayedById(R.id.textStatus)
            }
        }

        fun signOutWhileConnectedWarningMessageIsDisplayed() {
            if (isTvMainScreenRedesign()) {
                node.withText(R.string.tv_signout_dialog_description_connected).await { assertIsDisplayed() }
            } else {
                checkIfDialogContainsText(R.string.tv_signout_dialog_description_connected)
            }
        }
    }

    inline fun verify(block: Verify.() -> Unit) = Verify().apply(block)
}

private fun countryTile(countryCode: String) = node.withText(CountryTools.getFullName(countryCode))

