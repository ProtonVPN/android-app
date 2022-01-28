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

import androidx.test.espresso.Espresso
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import com.protonvpn.TestSettings
import com.protonvpn.android.R
import com.protonvpn.base.BaseRobot
import com.protonvpn.base.BaseVerify
import com.protonvpn.testsHelper.ServiceTestHelper

/**
 * [MapRobot] Contains all actions and verifications for map screen
 */
class MapRobot : BaseRobot() {

    fun clickConnectButtonWithoutVpnHandling(): MapRobot = clickElement(connectButtonInMap())

    fun clickCancelConnectionButton(): MapRobot = clickElement(R.id.buttonCancel)

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

    fun clickOnCountryNode(country: String): MapRobot {
        view.waitForCondition {
            clickElementByContentDescription<Any>(country)
            Espresso.onView(ViewMatchers.withContentDescription("$country Selected"))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        }
        return this
    }

    private fun connectButtonInMap() =
        view.isDescendantOf(view.withId(R.id.mapView)).withId(R.id.buttonConnect)

    class Verify : BaseVerify() {
        fun isDisconnectedFromVpn() = ServiceTestHelper().checkIfDisconnectedFromVPN()

        fun isCountryNodeSelected(country: String) =
            checkIfElementIsDisplayedByContentDesc("$country Selected")

        fun isCountryNodeNotSelected(country: String) =
            checkIfElementDoesNotExistByContentDesc("$country Selected")
    }

    inline fun verify(block: Verify.() -> Unit) = Verify().apply(block)
}
