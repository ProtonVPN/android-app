/*
 * Copyright (c) 2021 Proton AG
 * This file is part of Proton AG and ProtonCore.
 *
 * ProtonCore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonCore is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonCore.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.protonvpn.testsTv.actions

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.protonvpn.android.R
import com.protonvpn.base.BaseRobot
import com.protonvpn.data.Timeouts
import com.protonvpn.testsTv.verification.ConnectionVerify

/**
 * [TvCountryListRobot] Contains all actions and verifications for home view
 */
class TvCountryListRobot : BaseRobot() {

    private val uiDevice: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    fun connectToRecommendedCountry() : TvCountryListRobot = clickElementByText(R.string.tv_quick_connect_recommened)
    fun disconnectFromCountry() : TvCountryListRobot = clickElementByText(R.string.disconnect)
    fun confirmSignOut() : TvLoginRobot = clickDialogElementByText(R.string.dialog_sign_out_action)
    fun cancelSignOut() : TvCountryListRobot = clickDialogElementByText(R.string.cancel)
    fun getConnectionStatus() : String = getText(onView(withId(R.id.textStatus)))

    fun waitUntilCountryIsLoaded(country: String) : TvCountryListRobot {
        view.withText(country).isCompletelyDisplayed()
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

    fun signOut() : TvCountryListRobot {
        view.waitForCondition(watchTimeout = Timeouts.TWENTY_SECONDS_MS) {
            uiDevice.pressDPadDown()
            onView(withText(R.string.tv_signout_label)).check(matches(isDisplayed()))
        }
        uiDevice.pressDPadDown()

        // Note: ideally we would move right until the sign-out button is focused.
        uiDevice.pressDPadRight()
        uiDevice.pressDPadRight()
        uiDevice.pressDPadCenter()
        return TvCountryListRobot()
    }

    private fun pressFavourite() : TvCountryListRobot = clickElementByText(R.string.tv_quick_connect_favourite)

    class Verify : ConnectionVerify(){
        fun userIsLoggedIn() = checkIfElementIsDisplayedById(R.id.textStatus)

        fun signOutWhileConnectedWarningMessageIsDisplayed() =
            checkIfDialogContainsText(R.string.tv_signout_dialog_description_connected)
    }

    inline fun verify(block: Verify.() -> Unit) = Verify().apply(block)
}
