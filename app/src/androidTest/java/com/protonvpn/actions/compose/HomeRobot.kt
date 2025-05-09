package com.protonvpn.actions.compose

import com.protonvpn.actions.compose.interfaces.Robot
import com.protonvpn.android.R
import me.proton.core.test.android.robots.auth.AddAccountRobot
import me.proton.test.fusion.Fusion.node

object HomeRobot : Robot {

    private val settingsButton get() = node.withText(R.string.botton_nav_settings)
    private val profilesButton get() = node.withText(R.string.bottom_nav_profiles)
    private val signOutButton get() = node.withText(R.string.settings_sign_out)
    private val cancelButton get() = node.withText(R.string.dialog_action_cancel)
    private val confirmButton get() = node.withTag("confirmButton")
    private val dismissButton get() = node.withTag("dismissButton")
    private val connectionDetailsButton get() = node.withContentDescription(R.string.connection_card_accessbility_label_connection_details)
    private val signOutTitle get() = node.withText(R.string.dialog_sign_out_title)
    private val homeButton get() = node.withText(R.string.bottom_nav_home)
    private val retryButton get() = node.withText(R.string.retry)

    fun cancelLogout() = cancelButton.clickTo(this)

    fun openConnectionPanel() = connectionDetailsButton.clickTo(ConnectionPanelRobot)

    fun navigateToHome(): HomeRobot = homeButton.clickTo(this)

    fun navigateToSettings(): SettingsRobot = settingsButton.clickTo(SettingsRobot)
    fun navigateToProfiles(): ProfilesRobot = profilesButton.clickTo(ProfilesRobot)

    fun confirmLogout(): AddAccountRobot {
        confirmButton.click()
        return AddAccountRobot()
    }

    fun logout(): AddAccountRobot {
        settingsButton.click()
        signOutButton.scrollTo().click()
        return AddAccountRobot()
    }

    fun isLoggedIn() = homeButton.await { assertIsDisplayed() }

    fun autoLoginIncorrectCredentialsIsDisplayed() = retryButton.await { assertIsDisplayed() }

    fun signOutWarningMessageIsDisplayed() =
        dismissButton.await { assertIsDisplayed() } then
        confirmButton.await { assertIsDisplayed() } then
        signOutTitle.await { assertIsDisplayed() }
}
