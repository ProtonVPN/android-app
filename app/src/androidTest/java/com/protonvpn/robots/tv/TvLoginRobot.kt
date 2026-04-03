/*
 * Copyright (c) 2026. Proton AG
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

package com.protonvpn.robots.tv

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.text
import com.protonvpn.android.R
import me.proton.test.fusion.Fusion.device
import me.proton.test.fusion.Fusion.node
import me.proton.test.fusion.data.Robot

class TvLoginRobot : Robot {

    private val textSignInMessage get() = node.withText(R.string.session_fork_qr_code_message)
    private val textErrorNetworkMessage get() =
        node.withText(R.string.session_fork_qr_code_error_network)
    private val buttonCreateNewCode get() =
        node.withText(R.string.session_fork_qr_code_error_create_new_button)
    private val textCode get() =
        node.withTextSubstring("Enter the code:")

    fun navigateToTroubleSigningIn() {
        // There are issues with focus in compose, pressing down twice seems to help.
        device.pressKeyCode(KeyEvent.KEYCODE_DPAD_DOWN)
        device.pressKeyCode(KeyEvent.KEYCODE_DPAD_DOWN)
        device.pressEnter()
    }

    fun getUserCode(): String {
        waitFor {
            textCode.assertIsDisplayed()
        }
        val text = textCode.interaction.fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .first { it.startsWith("Enter the code") }
        return text.takeLast(9).filterNot { it.isWhitespace() }.toString()


    }

    fun waitForQrCode() {
        waitFor {
            textSignInMessage.assertIsDisplayed()
        }
    }

    fun assertErrorNetwork() {
        textErrorNetworkMessage.assertIsDisplayed()
    }

    fun clickCreateNewCode() {
        buttonCreateNewCode.assertIsDisplayed()
        device.pressKeyCode(KeyEvent.KEYCODE_ENTER)
    }

    override fun robotDisplayed() {
        waitForQrCode()
    }
}