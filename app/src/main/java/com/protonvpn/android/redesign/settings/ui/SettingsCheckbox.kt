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

package com.protonvpn.android.redesign.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.protonvpn.android.base.ui.ProtonVpnPreview
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun SettingsCheckbox(
    title: String,
    description: String,
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .toggleable(
                value,
                onValueChange = onValueChange,
                role = Role.Checkbox
            )
            .padding(vertical = 8.dp, horizontal = 16.dp),
    ) {
        Checkbox(
            checked = value,
            colors = CheckboxDefaults.colors(uncheckedColor = ProtonTheme.colors.shade60),
            onCheckedChange = null,
            modifier = Modifier.clearAndSetSemantics {}
        )
        Column(
            Modifier.padding(start = 12.dp)
        ) {
            Text(title, style = ProtonTheme.typography.body1Regular)
            Text(
                description,
                modifier = Modifier.padding(top = 4.dp),
                style = ProtonTheme.typography.body2Regular,
                color = ProtonTheme.colors.textWeak
            )
        }
    }
}

@Preview
@Composable
fun PreviewProtonDialogCheckbox() {
    ProtonVpnPreview {
        SettingsCheckbox(
            title = "Title",
            description = "Description",
            value = true,
            onValueChange = {}
        )
    }
}