/*
 * Copyright (c) 2023-2026. Proton AG
 *
 *  This file is part of ProtonVPN.
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

package com.protonvpn.android.ui_automator_test_util.robots

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import com.protonvpn.android.ui_automator_test_util.data.TestConstants
import me.proton.test.fusion.Fusion.byObject

object HomeRobot {

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    fun connect(): HomeRobot {
        byObject.withText("Connect").click()
        allowVpnPermission()
        return this
    }

    fun disconnect(): HomeRobot {
        byObject.withText("Disconnect").click()
        return this
    }

    fun waitUntilConnected(): HomeRobot {
        byObject.withText("Disconnect").waitForExists(TestConstants.TWENTY_SECOND_TIMEOUT)
        return this
    }

    fun navigateToCountries(): CountriesRobot {
        byObject.withText("Countries").click()
        return CountriesRobot
    }

    fun dismissNotificationRequest(): HomeRobot {
        byObject.withText("No thanks").click()
        return this
    }

    fun allowVpnPermission(): HomeRobot {
        if (isAllowVpnRequestVisible()) {
            device.findObject(UiSelector().textContains("OK")).click()
        }
        return this
    }

    private fun isAllowVpnRequestVisible(): Boolean {
        device.wait(Until.hasObject(By.text("OK")), TestConstants.FIVE_SECONDS_TIMEOUT_MS)
        return device.findObject(UiSelector().textContains("Connection request")).exists()
    }
}