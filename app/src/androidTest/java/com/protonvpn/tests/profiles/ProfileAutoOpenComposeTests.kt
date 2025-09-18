/*
 * Copyright (c) 2025 Proton AG
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

package com.protonvpn.tests.profiles

import android.net.Uri
import com.protonvpn.android.R
import com.protonvpn.android.profiles.data.ProfileAutoOpen
import com.protonvpn.android.profiles.ui.AutoOpenModal
import com.protonvpn.android.ui.settings.LabeledItem
import com.protonvpn.testRules.setVpnContent
import kotlinx.coroutines.test.runTest
import me.proton.test.fusion.Fusion.node
import me.proton.test.fusion.ui.compose.FusionComposeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val INVALID_URL = "https://proton,me"
private const val GOOD_URL = "https://proton.me"
private val APPS = listOf(
    LabeledItem("com.example", "My App", iconRes = R.drawable.ic_vpn_icon_colorful),
    LabeledItem("com.example2", "Not My App", iconRes = R.drawable.ic_launcher_notes_foreground)
)

class ProfileAutoOpenComposeTests : FusionComposeTest() {

    @Test
    fun invalidUrlShouldDisplayError() {
        val autoOpen = ProfileAutoOpen.Url(Uri.parse(INVALID_URL), openInPrivateMode = false)
        var changed = false
        var dismissed = false
        composeRule.setVpnContent {
            AutoOpenModal(autoOpen, { changed = true }, { dismissed = true },
                ::getAppInfo, getAllAppsInfo = { APPS }, showPrivateBrowsing = true)
        }
        node.withText(INVALID_URL).assertIsDisplayed()
        node.withText(R.string.saveButton).click()

        node.withText(R.string.profile_auto_open_error_invalid_url).assertIsDisplayed()
        assertFalse(dismissed)
        assertFalse(changed)
    }

    @Test
    fun saveButtonInactiveWhenNoAppSelected() {
        val autoOpen = ProfileAutoOpen.App("")
        var changed = false
        var dismissed = false
        composeRule.setVpnContent {
            AutoOpenModal(autoOpen, { changed = true }, { dismissed = true },
                ::getAppInfo, getAllAppsInfo = { APPS }, showPrivateBrowsing = true)
        }
        node.withText(R.string.saveButton).isDisabled()
        node.withText(R.string.saveButton).click()
        assertFalse(dismissed)
        assertFalse(changed)
    }

    @Test
    fun showPrivateBrowsingNotVisibleWhenFlagIsOff() = runTest {
        val autoOpen = ProfileAutoOpen.Url(Uri.parse(GOOD_URL), openInPrivateMode = false)
        composeRule.setVpnContent {
            AutoOpenModal(autoOpen, {}, {},
                ::getAppInfo, getAllAppsInfo = { APPS }, showPrivateBrowsing = false)
        }
        node.withText(R.string.create_profile_auto_open_url_private_mode_label).assertDoesNotExist()
    }

    @Test
    fun whenNoAppsAvailableZeroStateIsShown() = runTest {
        val autoOpen = ProfileAutoOpen.App("")
        composeRule.setVpnContent {
            AutoOpenModal(initialValue = autoOpen, onChange = {}, onDismissRequest = {},
                getAppInfo = { null }, getAllAppsInfo = { emptyList() }, showPrivateBrowsing = true)
        }
        node.withText(R.string.create_profile_auto_open_app_input_hint).click()
        composeRule.awaitIdle()
        node.withText(R.string.create_profile_auto_open_app_no_apps).assertIsDisplayed()
    }

    @Test
    fun selectedAppIsSuccessfullySaved() = runTest {
        val autoOpen = ProfileAutoOpen.App("")
        var newAutoOpen : ProfileAutoOpen? = null
        var dismissed = false
        composeRule.setVpnContent {
            AutoOpenModal(autoOpen, { newAutoOpen = it }, { dismissed = true },
                getAppInfo = ::getAppInfo, getAllAppsInfo = { APPS }, showPrivateBrowsing = true)
        }
        node.withText(R.string.create_profile_auto_open_app_input_hint).click()
        composeRule.awaitIdle()
        node.withText("My App").click()
        composeRule.awaitIdle()
        node.withText("My App").assertIsDisplayed()
        node.withText(R.string.saveButton).click()

        assertTrue(dismissed)
        assertEquals(ProfileAutoOpen.App("com.example"), newAutoOpen)
    }

    private fun getAppInfo(id: String) = APPS.firstOrNull { it.id == id }
}