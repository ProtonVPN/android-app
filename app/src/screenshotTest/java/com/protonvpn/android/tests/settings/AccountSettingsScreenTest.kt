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

package com.protonvpn.android.tests.settings

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.protonvpn.android.annotations.ProtonVpnTestPreview
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.redesign.settings.ui.AccountSettings
import com.protonvpn.android.redesign.settings.ui.SettingsViewModel
import me.proton.core.domain.entity.UserId

private const val SCREENSHOT_DISPLAY_NAME = "plus.user@proton.me"

@PreviewTest
@ProtonVpnTestPreview
@Composable
fun AccountSettingsPlusUserScreen() {
    ProtonVpnPreview(addSurface = true) {
        AccountSettings(
            viewState = SettingsViewModel.AccountSettingsViewState(
                userId = UserId("screenshot-user"),
                displayName = SCREENSHOT_DISPLAY_NAME,
                planDisplayName = "VPN Plus",
                recoveryEmail = "recovery@proton.me",
                passwordHint = null,
                upgradeToPlusBanner = false,
                isFido2Enabled = false,
                registeredSecurityKeys = emptyList(),
            ),
            onChangePassword = {},
            onChangeRecoveryEmail = {},
            onSecurityKeysClicked = {},
            onOpenMyAccount = {},
            onDeleteAccount = {},
            onUpgrade = {},
            onClose = {},
        )
    }
}

@PreviewTest
@ProtonVpnTestPreview
@Composable
fun AccountSettingsFreeUserScreen() {
    ProtonVpnPreview(addSurface = true) {
        AccountSettings(
            viewState = SettingsViewModel.AccountSettingsViewState(
                userId = UserId("screenshot-user"),
                displayName = SCREENSHOT_DISPLAY_NAME,
                planDisplayName = null,
                recoveryEmail = null,
                passwordHint = null,
                upgradeToPlusBanner = true,
                isFido2Enabled = false,
                registeredSecurityKeys = emptyList(),
            ),
            onChangePassword = {},
            onChangeRecoveryEmail = {},
            onSecurityKeysClicked = {},
            onOpenMyAccount = {},
            onDeleteAccount = {},
            onUpgrade = {},
            onClose = {},
        )
    }
}
