/*
 * Copyright (c) 2021 Proton Technologies AG
 * This file is part of Proton Technologies AG and ProtonCore.
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

import android.view.KeyEvent
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.protonvpn.base.BaseRobot
import com.protonvpn.android.R
import com.protonvpn.testsTv.matchers.UiElementMatcher
import com.protonvpn.testsTv.verification.ConnectionVerify
import me.proton.core.test.android.instrumented.builders.OnView
import me.proton.core.test.android.instrumented.waits.UIWaits

/**
 * [TvCountryListRobot] Contains all actions and verifications for home view
 */
class TvCountryListRobot : BaseRobot() {

    fun connectToRecommendedCountry() : TvCountryListRobot = clickElementByText(R.string.tv_quick_connect_recommened)
    fun disconnectFromCountry() : TvCountryListRobot = clickElementByText(R.string.disconnect)
    fun confirmSignOut() : TvLoginRobot = clickElementById(R.id.md_buttonDefaultPositive)
    fun cancelSignOut() : TvCountryListRobot = clickElementById(R.id.md_buttonDefaultNegative)
    fun getConnectionStatus() : String = UiElementMatcher().getText(onView(withId(R.id.textStatus)))

    fun connectToFavouriteCountry() : TvCountryListRobot {
        pressFavourite()
        pressFavourite()
        return TvCountryListRobot()
    }

    fun openFirstCountryConnectionWindow() : TvDetailedCountryRobot {
        view
                .withText(R.string.tv_quick_connect_recommened)
                .customAction(ViewActions.pressKey(KeyEvent.KEYCODE_DPAD_DOWN))
                .customAction(ViewActions.pressKey(KeyEvent.KEYCODE_DPAD_CENTER))
        return TvDetailedCountryRobot()
    }

    fun signOut() : TvCountryListRobot {
        UIWaits.performActionUntilMatcherFulfilled(
                onView(withId(R.id.container_list)),
                matches(isDisplayed()),
                withText(R.string.tv_signout_label),
                ViewActions.pressKey(KeyEvent.KEYCODE_DPAD_DOWN))

        view
                .withId(R.id.container_list)
                .customAction(ViewActions.pressKey(KeyEvent.KEYCODE_DPAD_DOWN))
                .customAction(ViewActions.pressKey(KeyEvent.KEYCODE_DPAD_CENTER))
        return TvCountryListRobot()
    }

    private fun pressFavourite() : TvCountryListRobot = clickElementByText(R.string.tv_quick_connect_favourite)

    class Verify : ConnectionVerify(){
        fun userIsLoggedIn(): OnView = checkIfElementIsDisplayedById(R.id.textStatus)

        fun signOutWhileConnectedWarningMessageIsDisplayed() : OnView =
                checkIfElementByIdContainsText(R.id.md_content,R.string.tv_signout_dialog_description_connected)
    }

    inline fun verify(block: Verify.() -> Unit) = Verify().apply(block)
}
