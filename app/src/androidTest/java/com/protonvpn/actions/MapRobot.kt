/*
 *  Copyright (c) 2021 Proton AG
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
import androidx.test.espresso.Espresso
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import com.protonvpn.TestSettings
import com.protonvpn.android.R
import com.protonvpn.base.BaseRobot
import com.protonvpn.base.BaseVerify
import com.protonvpn.testsHelper.ServiceTestHelper
import org.hamcrest.Matcher

/**
 * [MapRobot] Contains all actions and verifications for map screen
 */
class MapRobot : BaseRobot() {

    fun clickConnectButtonWithoutVpnHandling(): MapRobot = clickElement(connectButtonInMap())

    fun clickCancelConnectionButton(): MapRobot = clickElement(R.id.buttonCancel)

    fun clickUpgrade(): MapRobot {
        view.withId(R.id.buttonUpgrade).withAncestor(view.withId(R.id.mapView)).click()
        return this
    }

    fun clickConnectButton(): ConnectionRobot {
        clickElement<MapRobot>(connectButtonInMap())
        if (!TestSettings.mockedConnectionUsed) {
            HomeRobot().allowVpnToBeUsed()
        }
        return ConnectionRobot()
    }

    fun clickOnCountryNodeUntilConnectButtonAppears(country: String): MapRobot {
        view.waitForCondition {
            clickElementByContentDescription<Any>(country)
            Espresso.onView(ViewMatchers.withText(R.string.connect))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        }
        return this
    }

    fun clickOnCountryNodeSecureCore(country: String): MapRobot {
        clickOnCountryNodeUntilMatcherIsDisplayed(
            country,
            ViewMatchers.withContentDescription("$country Selected")
        )
        return this
    }

    fun clickOnCountryNode(country: String): MapRobot {
        clickOnCountryNodeUntilMatcherIsDisplayed(country, ViewMatchers.withText(country))
        return this
    }

    private fun clickOnCountryNodeUntilMatcherIsDisplayed(country: String, matcher: Matcher<View>) {
        view.waitForCondition {
            clickElementByContentDescription<Any>(country)
            Espresso.onView(matcher)
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        }
    }

    private fun connectButtonInMap() =
        view.isDescendantOf(view.withId(R.id.mapView)).withId(R.id.buttonConnect)

    class Verify : BaseVerify() {

        fun upgradeButtonIsVisible() {
            view.withId(R.id.buttonUpgrade).withAncestor(view.withId(R.id.mapView)).checkDisplayed()
        }

        fun isDisconnectedFromVpn() = ServiceTestHelper().checkIfDisconnectedFromVPN()

        fun isCountryNodeSelected(country: String) =
            checkIfElementIsDisplayedByContentDesc("$country Selected")

        fun isCountryNodeNotSelected(country: String) =
            checkIfElementDoesNotExistByContentDesc("$country Selected")
    }

    inline fun verify(block: Verify.() -> Unit) = Verify().apply(block)
}
