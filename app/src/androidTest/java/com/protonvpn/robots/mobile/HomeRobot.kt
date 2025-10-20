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

import com.protonvpn.interfaces.Robot
import com.protonvpn.android.R
import me.proton.core.test.android.robots.auth.AddAccountRobot
import me.proton.test.fusion.Fusion.node

object HomeRobot : Robot {

    private val settingsButton get() =
        node.hasAncestor(node.withTag("mainBottomBar")).withText(R.string.botton_nav_settings)
    private val profilesButton get() =
        node.hasAncestor(node.withTag("mainBottomBar")).withText(R.string.bottom_nav_profiles)
    private val homeButton get() = node.hasAncestor(node.withTag("mainBottomBar")).withText(R.string.bottom_nav_home)
    private val signOutButton get() = node.withText(R.string.settings_sign_out)
    private val cancelButton get() = node.withText(R.string.dialog_action_cancel)
    private val confirmButton get() = node.withTag("confirmButton")
    private val dismissButton get() = node.withTag("dismissButton")
    private val connectionDetailsButton get() = node.withContentDescription(R.string.connection_card_accessbility_label_connection_details)
    private val signOutTitle get() = node.withText(R.string.dialog_sign_out_title)
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

    fun isHomeDisplayed() = node.withText(R.string.vpn_status_disabled).assertIsDisplayed()

    fun autoLoginIncorrectCredentialsIsDisplayed() = retryButton.await { assertIsDisplayed() }

    fun signOutWarningMessageIsDisplayed() =
        dismissButton.await { assertIsDisplayed() } then
        confirmButton.await { assertIsDisplayed() } then
        signOutTitle.await { assertIsDisplayed() }
}
