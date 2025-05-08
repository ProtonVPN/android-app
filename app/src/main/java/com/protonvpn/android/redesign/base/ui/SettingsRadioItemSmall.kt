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

package com.protonvpn.android.redesign.base.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.protonvpn.android.base.ui.ProtonVpnPreview
import me.proton.core.compose.component.VerticalSpacer
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun SettingsRadioItemSmall(
    title: String,
    description: String?,
    selected: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier.Companion,
    titleColor: Color = Color.Companion.Unspecified,
    horizontalContentPadding: Dp = 0.dp,
    trailingTitleContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .selectable(selected, onClick = onSelected)
            .padding(vertical = 12.dp, horizontal = horizontalContentPadding),
        verticalAlignment = Alignment.Companion.CenterVertically
    ) {
        if (leadingContent != null) {
            leadingContent()
        }
        Column(
            modifier = Modifier.Companion.weight(1f)
        ) {
            Row(verticalAlignment = Alignment.Companion.CenterVertically) {
                Text(
                    title,
                    style = ProtonTheme.typography.body2Regular,
                    color = titleColor,
                    modifier = Modifier.Companion.weight(1f, fill = false)
                )
                if (trailingTitleContent != null) {
                    Spacer(Modifier.Companion.width(8.dp))
                    trailingTitleContent()
                }
            }
            if (description != null) {
                VerticalSpacer(height = 4.dp)
                Text(
                    description,
                    style = ProtonTheme.typography.body2Regular,
                    color = ProtonTheme.colors.textWeak
                )
            }
        }
        RadioButton(
            selected = selected,
            onClick = null,
            modifier = Modifier.Companion
                .clearAndSetSemantics {}
                .padding(start = 8.dp)
        )
    }
}

@ProtonVpnPreview
@Composable
private fun RadioButtonPreview() {
    ProtonVpnPreview {
        SettingsRadioItemSmall(
            title = "Radio option",
            description = "Long radio button description. Long radio button description. Long radio button description.",
            selected = true,
            onSelected = {},
            horizontalContentPadding = 16.dp,
        )
    }
}
