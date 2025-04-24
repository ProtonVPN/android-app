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

import android.view.KeyEvent
import com.protonvpn.interfaces.Robot
import me.proton.core.auth.test.robot.signup.SignUpRobot
import me.proton.test.fusion.Fusion.byObject
import me.proton.test.fusion.Fusion.device
import me.proton.test.fusion.Fusion.node

object SettingsRobot : Robot {
    /** Visible only for credential-less account. */
    private val createAccountButton
        get() = node.withText(me.proton.core.auth.presentation.R.string.auth_create_account)
    private val gearIconButton get() = byObject.withContentDesc("Settings")
    private val alwaysOnVpnButton get() = byObject.withText("Always-on VPN")
    private val alwaysOnProtonVpnButton get() = byObject.withText("Proton VPN")

    /** Only for credential-less account. */
    fun createAccount(): SignUpRobot {
        createAccountButton.click()
        return SignUpRobot
    }

    fun enableAlwaysOn(): SettingsRobot {
        gearIconButton.click()
        alwaysOnVpnButton.click()
        return this
    }

    fun goBack(): SettingsRobot {
        device.pressKeyCode(KeyEvent.KEYCODE_BACK)
        return this
    }

    fun alwaysOnOpenProtonVpn(): HomeRobot {
        alwaysOnProtonVpnButton.click()
        return HomeRobot
    }

    fun usernameIsDisplayed(name: String) = node.withText(name).await { assertIsDisplayed() }
}
