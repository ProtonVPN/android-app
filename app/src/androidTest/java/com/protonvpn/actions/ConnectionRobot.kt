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
import com.protonvpn.android.R
import com.protonvpn.base.BaseRobot
import com.protonvpn.base.BaseVerify
import com.protonvpn.testsHelper.ServerManagerHelper
import com.protonvpn.testsHelper.ServiceTestHelper
import junit.framework.TestCase.assertFalse
import kotlin.test.assertTrue

/**
 * [ConnectionRobot] Contains all actions and verifications for connection steps
 */
class ConnectionRobot : BaseRobot() {

    private val stateMonitor get() = ServerManagerHelper().vpnStateMonitor

    fun clickCancelConnectionButton(): ConnectionRobot = clickElementById(R.id.buttonCancel)

    fun clickCancelRetry() : ConnectionRobot = clickElementById(R.id.buttonCancelRetry)

    fun disconnectFromVPN() : ConnectionRobot {
        view.waitForCondition {
            clickElementById<Any>(R.id.buttonDisconnect)
            assertFalse(stateMonitor.isConnected)
        }
        return waitUntilDisplayedByText(R.string.loaderNotConnected)
    }

    fun clickOnConnectButtonUntilConnected(profileName: String): ConnectionRobot {
        view.waitForCondition {
            clickElementByIdAndContentDescription<Any>(R.id.buttonConnect, profileName)
            Espresso.onView(ViewMatchers.withId(R.id.buttonDisconnect))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        }
        return ConnectionRobot()
    }

    class Verify : BaseVerify(){

        fun isConnected(){
            isConnectedServiceHelper()
            checkIfElementIsDisplayedById(R.id.buttonDisconnect)
        }

        fun isDisconnected(){
            isDisconnectedServiceHelper()
            checkIfElementIsDisplayedById(R.id.textNotConnectedSuggestion)
        }

        fun isDisconnectedServiceHelper(){
            ServiceTestHelper().checkIfDisconnectedFromVPN()
        }

        fun isConnectedServiceHelper(){
            ServiceTestHelper().checkIfConnectedToVPN()
        }

        fun isConnectingToSecureCoreServer(){
            assertTrue(ServerManagerHelper().vpnStateMonitor.isConnectingToSecureCore)
        }

        fun isNotReachableErrorDisplayed() =
            checkIfElementByIdContainsText(R.id.textError, R.string.error_server_unreachable)
    }

    inline fun verify(block: Verify.() -> Unit) = Verify().apply(block)
}
