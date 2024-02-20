/*
 * Copyright (c) 2024 Proton Technologies AG
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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import me.proton.core.presentation.R as CoreR

@Composable
fun SplitTunnelingSubSetting(
    onClose: () -> Unit,
    splitTunneling: SettingsViewModel.SettingViewState.SplitTunneling,
    onLearnMore: () -> Unit,
    onSplitTunnelToggle: () -> Unit,
    onExcludedAppsClick: () -> Unit,
    onExcludedIpsClick: () -> Unit,
) {
    SubSetting(
        title = stringResource(id = splitTunneling.titleRes),
        onClose = onClose
    ) {
        Image(
            painter = painterResource(id = R.drawable.split_tunneling_large),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
        splitTunneling.ToToggle(
            onToggle = onSplitTunnelToggle,
            onAnnotatedClick = onLearnMore,
        )
        val splitTunnelingSettings = splitTunneling.splitTunnelingSettings
        AnimatedVisibility(
            visible = splitTunnelingSettings.isEnabled,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column {
                SettingRowWithIcon(
                    icon = CoreR.drawable.ic_proton_mobile,
                    title = stringResource(id = R.string.settings_split_tunneling_excluded_apps),
                    subtitle = formatExcludedItems(splitTunneling.splitTunnelAppNames),
                    onClick = onExcludedAppsClick
                )

                SettingRowWithIcon(
                    icon = CoreR.drawable.ic_proton_window_terminal,
                    title = stringResource(id = R.string.settings_split_tunneling_excluded_ips),
                    subtitle = formatExcludedItems(splitTunnelingSettings.excludedIps),
                    onClick = onExcludedIpsClick
                )
            }
        }
    }
}

@Composable
private fun formatExcludedItems(excludedItems: List<String>): String {
    return when {
        excludedItems.isEmpty() -> stringResource(id = R.string.settings_split_tunneling_empty)
        excludedItems.size == 1 -> excludedItems.first()
        excludedItems.size == 2 -> "${excludedItems[0]}, ${excludedItems[1]}"
        else -> stringResource(
            id = R.string.settings_split_tunneling_excluded_format,
            excludedItems[0], excludedItems[1], excludedItems.size - 2
        )
    }
}
