/*
 * Copyright (c) 2024 Proton AG
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

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.protonvpn.android.R

@Composable
fun VpnAccelerator(
    onClose: () -> Unit,
    value: Boolean,
    onLearnMore: () -> Unit,
    onToggle: () -> Unit,
) {
    SubSetting(
        title = stringResource(id = R.string.settings_vpn_accelerator_title),
        onClose = onClose
    ) {
        SettingsToggleItem(
            name = stringResource(id = R.string.settings_vpn_accelerator_title),
            description = stringResource(
                id = R.string.settings_vpn_accelerator_description,
                stringResource(id = R.string.learn_more)
            ),
            annotatedPart = stringResource(id = R.string.learn_more),
            onAnnotatedClick = onLearnMore,
            value = value,
            onToggle = onToggle,
        )
    }
}
