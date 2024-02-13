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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.AnnotatedClickableText
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.captionWeak
import me.proton.core.compose.theme.defaultNorm

@Composable
fun BaseSettingsItem(
    modifier: Modifier = Modifier,
    name: String,
    description: String,
    annotatedPart: String? = null,
    onAnnotatedClick: (() -> Unit)? = null,
    actionComposable: @Composable () -> Unit
) {
    Column(modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                style = ProtonTheme.typography.defaultNorm,
                modifier = Modifier.weight(1f)
            )
            actionComposable()
        }
        Spacer(modifier = Modifier.padding(top = 16.dp))
        if (annotatedPart != null && onAnnotatedClick != null) {
            AnnotatedClickableText(
                fullText = description,
                annotatedPart = annotatedPart,
                onAnnotatedClick = onAnnotatedClick,
                modifier = Modifier.padding(end = 8.dp)
            )
        } else {
            Text(
                text = description,
                style = ProtonTheme.typography.captionWeak,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}

@Composable
fun SettingsToggleItem(
    modifier: Modifier = Modifier,
    name: String,
    description: String,
    value: Boolean,
    annotatedPart: String? = null,
    onAnnotatedClick: (() -> Unit)? = null,
    onToggle: () -> Unit
) {
    BaseSettingsItem(modifier, name, description, annotatedPart, onAnnotatedClick) {
        Switch(
            checked = value,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors().copy(
                uncheckedBorderColor = ProtonTheme.colors.shade50,
                uncheckedTrackColor = ProtonTheme.colors.shade50,
                uncheckedThumbColor = ProtonTheme.colors.shade80,
            )
        )
    }
}

@Composable
fun SettingsItem(
    modifier: Modifier = Modifier,
    name: String,
    description: String,
    value: SettingsViewModel.SettingValue<Boolean>,
    annotatedPart: String? = null,
    onAnnotatedClick: (() -> Unit)? = null,
    onToggle: () -> Unit,
    onRestricted: () -> Unit,
) {
    when (value) {
        is SettingsViewModel.SettingValue.Restricted -> {
            BaseSettingsItem(modifier, name, description, annotatedPart, onAnnotatedClick) {
                Icon(
                    modifier = Modifier.clickable(onClick = onRestricted),
                    painter = painterResource(id = R.drawable.vpn_plus_badge),
                    tint = Color.Unspecified,
                    contentDescription = null,
                )
            }
        }
        is SettingsViewModel.SettingValue.Available -> {
            SettingsToggleItem(
                modifier = modifier,
                name = name,
                description = description,
                value = value.value,
                annotatedPart = annotatedPart,
                onAnnotatedClick = onAnnotatedClick,
                onToggle = onToggle
            )
        }
    }
}