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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.base.ui.previews.PreviewBooleanProvider
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun SettingsCheckbox(
    title: String,
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    verticalAlignment: Alignment.Vertical = if (description == null) Alignment.CenterVertically else Alignment.Top,
) {
    Row(
        modifier
            .toggleable(
                value,
                onValueChange = onValueChange,
                role = Role.Checkbox
            )
            .padding(vertical = 8.dp, horizontal = 16.dp),
        verticalAlignment = verticalAlignment,
    ) {
        Checkbox(
            checked = value,
            colors = CheckboxDefaults.colors(uncheckedColor = ProtonTheme.colors.shade60),
            onCheckedChange = null,
            modifier = Modifier.clearAndSetSemantics {}
        )

        Column(
            modifier = Modifier.padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(space = 4.dp),
        ) {
            Text(
                text = title,
                style = ProtonTheme.typography.body1Regular,
            )

            if (description != null) {
                Text(
                    text = description,
                    style = ProtonTheme.typography.body2Regular,
                    color = ProtonTheme.colors.textWeak,
                )
            }
        }
    }
}

@ProtonVpnPreview
@Composable
private fun PreviewProtonDialogCheckbox(
    @PreviewParameter(PreviewBooleanProvider::class) hasDescription: Boolean,
) {
    ProtonVpnPreview {
        SettingsCheckbox(
            title = "Title",
            description = "Description".takeIf { hasDescription },
            value = true,
            onValueChange = {}
        )
    }
}
