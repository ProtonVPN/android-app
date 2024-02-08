package com.protonvpn.actions.compose

import com.protonvpn.actions.compose.interfaces.Robot
import me.proton.core.auth.test.robot.signup.SignUpRobot
import me.proton.test.fusion.Fusion.node

object SettingsRobot : Robot {
    /** Visible only for credential-less account. */
    private val createAccountButton
        get() = node.withText(me.proton.core.auth.presentation.R.string.auth_create_account)

    /** Only for credential-less account. */
    fun createAccount(): SignUpRobot {
        createAccountButton.click()
        return SignUpRobot
    }

    fun usernameIsDisplayed(name: String) = node.withText(name).await { assertIsDisplayed() }
}
