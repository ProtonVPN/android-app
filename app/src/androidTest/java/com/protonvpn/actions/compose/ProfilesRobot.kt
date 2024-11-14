/*
 * Copyright (c) 2024. Proton AG
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
package com.protonvpn.actions.compose

import androidx.annotation.StringRes
import com.protonvpn.actions.compose.interfaces.Robot
import com.protonvpn.android.R
import me.proton.test.fusion.Fusion.node

object ProfilesRobot : Robot {

    private val createProfileButton
        get() = node.withText(R.string.profiles_button_create_profile)

    fun addProfile() = createProfileButton.clickTo(ProfileAddEditRobot)
    fun profileExists(name: String) = node.withText(name).await { assertIsDisplayed() }
    fun profileExists(@StringRes nameRes: Int) = node.withText(nameRes).await { assertIsDisplayed() }
    fun profileNotExists(name: String) = node.withText(name).await { assertIsNotDisplayed() }

    fun open(@StringRes nameRes: Int) =
        node.useUnmergedTree()
            .withTag("intentOpen")
            .hasAncestor(node.withTag("intentRow").hasDescendant(node.withText(nameRes)))
            .clickTo(this)

    fun open(name: String) =
        node.useUnmergedTree()
            .withTag("intentOpen")
            .hasAncestor(node.withTag("intentRow").hasDescendant(node.withText(name)))
            .clickTo(this)

    fun edit() = node.withText(R.string.profile_action_edit).clickTo(ProfileAddEditRobot)
    fun delete() = node.withText(R.string.profile_action_delete).clickTo(this)
    fun connect(name: String) = node.withText(name).clickTo(ConnectionRobot)
    fun zeroScreenDisplayed() = node.withText(R.string.profiles_zero_state_title).await { assertIsDisplayed() }
}

object ProfileAddEditRobot : Robot {
    private val profileNameField get() = node.withTag("profileName")
    private val nextButton get() = node.withText(R.string.create_profile_button_next)
    private val doneButton get() = node.withText(R.string.create_profile_button_done)

    fun setProfileName(name: String): ProfileAddEditRobot {
        profileNameField.replaceText(name)
        return this
    }

    fun next() = nextButton.clickTo(this)
    fun done() = doneButton.clickTo(ProfilesRobot)
}