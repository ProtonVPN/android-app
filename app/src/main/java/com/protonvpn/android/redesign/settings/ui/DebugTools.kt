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

package com.protonvpn.android.redesign.settings.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.protonvpn.android.base.ui.ProtonSecondaryButton
import com.protonvpn.android.redesign.base.ui.SettingsItem

@Composable
fun DebugTools(
    onConnectGuestHole: () -> Unit,
    onClose: () -> Unit,
) {
    SubSetting(
        title = "Debug tools",
        onClose = onClose
    ) {
        SettingsItem(
            name = "Enable Guest Hole",
            description = "Simulates a 10s API call that triggers Guest Hole.",
        ) {
            ProtonSecondaryButton(onClick = onConnectGuestHole) {
                Text("Connect")
            }
        }
    }
}
