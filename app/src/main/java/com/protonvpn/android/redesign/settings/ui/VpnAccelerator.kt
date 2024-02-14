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

@Composable
fun VpnAccelerator(
    onClose: () -> Unit,
    vpnAccelerator: SettingsViewModel.SettingViewState.VpnAccelerator,
    onLearnMore: () -> Unit,
    onToggle: () -> Unit,
) {
    SubSetting(
        title = stringResource(id = vpnAccelerator.titleRes),
        onClose = onClose
    ) {
        vpnAccelerator.ToToggle(
            onToggle = onToggle,
            onAnnotatedClick = onLearnMore,
        )
    }
}
