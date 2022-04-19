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

import com.protonvpn.android.R
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.base.BaseRobot
import com.protonvpn.base.BaseVerify
import com.protonvpn.data.DefaultData

/**
 * [RealConnectionRobot] Contains all actions and verifications for real connection tests
 */
class RealConnectionRobot : BaseRobot() {

    fun disconnectFromVPN() : RealConnectionRobot {
        clickElementById<RealConnectionRobot>(R.id.buttonDisconnect)
        return waitUntilDisplayedByText(R.string.loaderNotConnected)
    }

    fun connectThroughQuickConnectRealConnection() : RealConnectionRobot{
        HomeRobot().connectThroughQuickConnect(DefaultData.DEFAULT_CONNECTION_PROFILE)
        return waitUntilDisplayed(R.id.buttonDisconnect)
    }

    class Verify : BaseVerify() {

        fun checkIfDisconnected() = checkIfElementIsDisplayedById(R.id.textNotConnectedSuggestion)

        fun checkProtocol(protocol: ProtocolSelection) =
            checkIfElementByIdContainsText(R.id.textProtocol, protocol.displayName)

        fun checkIfConnectedAndCorrectIpAddressIsDisplayed(expectedId: String) {
            checkIfElementIsDisplayedById(R.id.buttonDisconnect)
            checkIfElementByIdContainsText(R.id.textServerIp, expectedId)
        }
    }

    inline fun verify(block: Verify.() -> Unit) = Verify().apply(block)
}
