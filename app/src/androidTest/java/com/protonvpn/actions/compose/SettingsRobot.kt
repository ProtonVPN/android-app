package com.protonvpn.actions.compose

import android.view.KeyEvent
import com.protonvpn.actions.compose.interfaces.Robot
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

    fun alwaysOnOpenProtonVpn(): HomeRobot{
        alwaysOnProtonVpnButton.click()
        return HomeRobot
    }

    fun usernameIsDisplayed(name: String) = node.withText(name).await { assertIsDisplayed() }
}
