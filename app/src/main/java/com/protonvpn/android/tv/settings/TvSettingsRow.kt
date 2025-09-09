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

package com.protonvpn.android.tv.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.RadioButton
import androidx.tv.material3.Switch
import androidx.tv.material3.SwitchColors
import androidx.tv.material3.SwitchDefaults
import androidx.tv.material3.Text
import me.proton.core.compose.component.VerticalSpacer
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.R as CoreR

@Composable
fun TvSettingsItem(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    @DrawableRes iconRes: Int? = null,
) {
    TvListRow(
        onClick,
        modifier = modifier,
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.padding(end = 12.dp)
            )
        }

        Column {
            Text(title)
            if (description != null) {
                VerticalSpacer(height = 4.dp)
                Text(
                    description,
                    style = ProtonTheme.typography.body2Regular,
                    color = ProtonTheme.colors.textWeak
                )
            }
        }
    }
}

@Composable
fun TvSettingsItemSwitch(
    title: String,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvListRow(
        onClick,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.semantics {
            toggleableState = ToggleableState(checked)
        },
    ) {
        Text(title, modifier = Modifier.weight(1f))
        TvProtonSwitch(
            checked = checked,
            onCheckedChange = {},
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

private val SwitchDefaults.protonColors @Composable get() = SwitchDefaults.colors().copy(
    uncheckedBorderColor = ProtonTheme.colors.shade50,
    uncheckedTrackColor = ProtonTheme.colors.shade50,
    uncheckedThumbColor = ProtonTheme.colors.shade80,
)

@Composable
fun TvProtonSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.protonColors,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    Switch(checked, onCheckedChange, modifier, thumbContent, enabled, colors, interactionSource)
}

@Composable
fun TvSettingsItemRadioSmall(
    title: String,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    clickSound: Boolean = true,
    description: String? = null,
    trailingTitleContent: (@Composable () -> Unit)? = null
) {
    TvListRow(
        onClick,
        verticalAlignment = Alignment.CenterVertically,
        verticalContentPadding = 12.dp,
        clickSound = clickSound,
        modifier = modifier.semantics {
            toggleableState = ToggleableState(checked)
        },
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = ProtonTheme.typography.body2Regular)
                if (trailingTitleContent != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    trailingTitleContent()
                }
            }
            if (description != null) {
                VerticalSpacer(height = 4.dp)
                Text(description, style = ProtonTheme.typography.body2Regular, color = ProtonTheme.colors.textWeak)
            }
        }
        RadioButton(
            selected = checked,
            onClick = null,
            modifier = Modifier
                .padding(start = 8.dp)
                .clearAndSetSemantics { }
        )
    }
}

@Composable
fun TvSettingsItemMoreInfo(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes leadingIconResId: Int = CoreR.drawable.ic_proton_question_circle,
) {
    TvListRow(
        modifier = modifier,
        onClick = onClick,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        Icon(
            painter = painterResource(id = leadingIconResId),
            contentDescription = null,
            tint = ProtonTheme.colors.textNorm,
        )

        Text(
            text = text,
            style = ProtonTheme.typography.body2Regular,
            color = ProtonTheme.colors.textNorm,
        )
    }
}
