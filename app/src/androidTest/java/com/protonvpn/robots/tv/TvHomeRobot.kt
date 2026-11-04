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

import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.protonvpn.android.R
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.base.BaseRobot
import com.protonvpn.base.BaseVerify
import me.proton.test.fusion.Fusion.node

/**
 * [TvHomeRobot] Contains all actions and verifications for the home view: VPN status and the list of countries.
 *
 * Supports both the legacy and the redesigned main screen, depending on the TvMainScreenRedesign feature flag.
 */
class TvHomeRobot : BaseRobot() {

    private val uiDevice: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    fun connectToRecommendedCountry(): TvHomeRobot =
        if (isTvMainScreenRedesign()) {
            clickNode(R.string.tv_vpn_status_action_connect_fastest)
        } else {
            clickElementByText(R.string.tv_quick_connect_recommened)
        }

    fun disconnectFromCountry(): TvHomeRobot =
        if (isTvMainScreenRedesign()) {
            clickNode(R.string.disconnect)
        } else {
            clickElementByText(R.string.disconnect)
        }

    fun waitUntilCountryIsLoaded(countryCode: String): TvHomeRobot {
        if (isTvMainScreenRedesign()) {
            countryTile(countryCode).await { assertExists() }
        } else {
            view.withText(countryCode).isCompletelyDisplayed()
        }
        return this
    }

    fun connectToFavouriteCountry(): TvHomeRobot =
        clickElementByText(R.string.tv_quick_connect_favourite)

    fun openFirstCountryConnectionWindow(): TvDetailedCountryRobot {
        waitUntilDisplayedByText<Any>(R.string.tv_quick_connect_recommened)
        uiDevice.pressDPadDown()
        uiDevice.pressDPadCenter()
        return waitUntilDisplayed(R.id.countryDescription)
    }

    fun connectToCountry(countryCode: String): TvHomeRobot {
        countryTile(countryCode).performSemanticsAction(SemanticsActions.OnClick)
        return this
    }

    fun connectToCity(countryCode: String, city: String): TvHomeRobot {
        countryTile(countryCode).performSemanticsAction(SemanticsActions.OnLongClick)
        node.withText(city).performSemanticsAction(SemanticsActions.OnClick)
        return this
    }

    private fun clickNode(textRes: Int): TvHomeRobot {
        node.withText(textRes).performSemanticsAction(SemanticsActions.OnClick)
        return this
    }

    class Verify : BaseVerify() {
        fun userIsConnected() {
            if (isTvMainScreenRedesign()) {
                node.withText(R.string.tv_vpn_status_protected).await { assertIsDisplayed() }
            } else {
                checkIfElementByIdContainsText(R.id.textStatus, getString(R.string.stateConnectedTo, ""))
            }
        }

        fun userIsDisconnected() {
            if (isTvMainScreenRedesign()) {
                node.withText(R.string.tv_vpn_status_unprotected).await { assertIsDisplayed() }
            } else {
                checkIfElementByIdContainsText(R.id.textStatus, getString(R.string.stateNotConnected))
            }
        }

        fun userIsConnectedToCountry(countryName: String) {
            checkIfElementByIdContainsText(R.id.textStatus, getString(R.string.stateConnectedTo, countryName))
        }

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
    }

    inline fun verify(block: Verify.() -> Unit) = Verify().apply(block)
}

private fun getString(textRes: Int, vararg args: Any): String =
    InstrumentationRegistry.getInstrumentation().targetContext.getString(textRes, *args)

private fun countryTile(countryCode: String) = node.withText(CountryTools.getFullName(countryCode))
