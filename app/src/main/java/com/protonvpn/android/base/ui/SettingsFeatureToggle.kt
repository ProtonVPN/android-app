/*
 * Copyright (c) 2025. Proton AG
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

package com.protonvpn.android.base.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultNorm

private val SettingsFeatureToggleShape = RoundedCornerShape(16.dp)

@Composable
fun SettingsFeatureToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(SettingsFeatureToggleShape)
            .background(ProtonTheme.colors.backgroundSecondary)
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = ProtonTheme.typography.defaultNorm, modifier = Modifier.weight(1f))
        ProtonSwitch(checked, onCheckedChange = null)
    }
}

@ProtonVpnPreview
@Composable
private fun SettingsFeatureTogglePreview() {
    ProtonVpnPreview {
        SettingsFeatureToggle(
            "Feature",
            checked = true,
            onCheckedChange = {},
            modifier = Modifier.widthIn(min = 300.dp)
        )
    }
}
