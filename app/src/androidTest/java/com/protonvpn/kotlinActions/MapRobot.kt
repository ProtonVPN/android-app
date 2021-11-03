/*
 * Copyright (c) 2021 Proton Technologies AG
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
package com.protonvpn.kotlinActions

import com.protonvpn.MockSwitch
import com.protonvpn.actions.HomeRobot
import com.protonvpn.android.R
import com.protonvpn.base.BaseRobot
import com.protonvpn.base.BaseVerify
import com.protonvpn.testsHelper.ConditionalActionsHelper
import com.protonvpn.testsHelper.ServiceTestHelper

class MapRobot : BaseRobot() {

    fun swipeDownToCloseConnectionInfoLayout() : MapRobot = swipeDownOnElementById(R.id.layoutBottomSheet)

    fun clickConnectButtonWithoutVpnHandling(): MapRobot = clickElement(connectButtonInMap())

    fun clickCancelConnectionButton(): MapRobot = clickElement(R.id.buttonCancel)

    fun clickConnectButton(): ConnectionRobot {
        clickElement<MapRobot>(connectButtonInMap())
        if (!MockSwitch.mockedConnectionUsed) {
            HomeRobot().allowToUseVpn()
        }
        return ConnectionRobot()
    }

    fun clickOnCountryNode(country: String): MapRobot {
        ConditionalActionsHelper().clickOnMapNodeUntilConnectButtonAppears(country)
        return this
    }

    private fun connectButtonInMap() =
        view.isDescendantOf(view.withId(R.id.mapView))
            .withId(R.id.buttonConnect)

    class Verify : BaseVerify(){
        fun isDisconnectedFromVpn() = ServiceTestHelper().checkIfDisconnectedFromVPN()

        fun isCountryNodeSelected(country: String) =
            checkIfElementIsDisplayedByContentDesc("$country Selected")

        fun isCountryNodeNotSelected(country: String) =
            checkIfElementDoesNotExistByContentDesc("$country Selected")
    }

    inline fun verify(block: Verify.() -> Unit) = Verify().apply(block)
}