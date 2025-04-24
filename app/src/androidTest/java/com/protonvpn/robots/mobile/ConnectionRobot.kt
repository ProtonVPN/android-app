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

package com.protonvpn.robots.mobile

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import com.protonvpn.interfaces.Robot
import com.protonvpn.android.R
import com.protonvpn.data.Timeouts
import me.proton.test.fusion.Fusion.node

object ConnectionRobot : Robot {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private val connectButton get() = node.withText(R.string.connect)
    private val disconnectButton get() = node.withText(R.string.disconnect)
    private val noThanksButton get() = node.withText(R.string.no_thanks)

    fun quickConnect() = connectButton.clickTo(this)
    fun disconnect() = disconnectButton.clickTo(this)
    fun dissmissNotifications() = noThanksButton.clickTo(this)

    fun allowVpnPermission(): ConnectionRobot {
        if (isAllowVpnRequestVisible()) {
            device.findObject(UiSelector().textContains("OK")).click()
        }
        return this
    }

    fun isConnected() = nodeWithTextDisplayed(R.string.vpn_status_connected)
    fun isDisconnected() = nodeWithTextDisplayed(R.string.vpn_status_disabled)

    private fun isAllowVpnRequestVisible(): Boolean {
        device.wait(Until.hasObject(By.text("OK")), Timeouts.FIVE_SECONDS_MS)
        return device.findObject(UiSelector().textContains("Connection request")).exists()
    }
}
