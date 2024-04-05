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

package com.protonvpn.android.redesign.base.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.protonvpn.android.base.ui.AnnotatedClickableText
import com.protonvpn.android.base.ui.ProtonSwitch
import com.protonvpn.android.base.ui.theme.VpnTheme
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.captionWeak
import me.proton.core.compose.theme.defaultNorm
import me.proton.core.compose.theme.defaultSmallNorm
import me.proton.core.compose.theme.defaultSmallWeak

data class ClickableTextAnnotation(
    val annotatedPart: String,
    val onAnnotatedClick: () -> Unit,
    val onAnnotatedOutsideClick: () -> Unit,
)

@Composable
fun BaseSettingsItem(
    modifier: Modifier = Modifier,
    name: String,
    description: String,
    subTitle: String? = null,
    descriptionAnnotation: ClickableTextAnnotation? = null,
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
        if (subTitle != null) {
            Text(
                text = subTitle,
                style = ProtonTheme.typography.defaultSmallWeak,
            )
        }
        val paddingModifier = Modifier.padding(end = 8.dp, top = 16.dp)
        if (descriptionAnnotation != null) {
            AnnotatedClickableText(
                fullText = description,
                annotatedPart = descriptionAnnotation.annotatedPart,
                onAnnotatedClick = descriptionAnnotation.onAnnotatedClick,
                onAnnotatedOutsideClick = descriptionAnnotation.onAnnotatedOutsideClick,
                style = ProtonTheme.typography.body2Regular,
                color = ProtonTheme.colors.textWeak,
                modifier = paddingModifier,
            )
        } else {
            Text(
                text = description,
                style = ProtonTheme.typography.body2Regular,
                color = ProtonTheme.colors.textWeak,
                modifier = paddingModifier
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
    subTitle: String? = null,
    descriptionAnnotation: ClickableTextAnnotation? = null,
    onToggle: () -> Unit
) {
    BaseSettingsItem(
        modifier.toggleable(value, onValueChange = { onToggle() }),
        name,
        description,
        subTitle,
        descriptionAnnotation
    ) {
        ProtonSwitch(
            checked = value,
            onCheckedChange = null,
        )
    }
}

@Composable
fun <T> SettingsRadioItem(
    modifier: Modifier,
    itemValue: T,
    selectedValue: T,
    onSelected: (T) -> Unit,
    label: String,
    description: String
) {
    Row(
        modifier = modifier
            .selectable(
                selected = itemValue == selectedValue,
                onClick = { onSelected(itemValue) }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.padding(end = 8.dp).weight(1f),
        ) {
            Text(
                text = label,
                style = ProtonTheme.typography.defaultSmallNorm,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = description,
                style = ProtonTheme.typography.body2Regular,
                color = ProtonTheme.colors.textWeak,
            )
        }
        RadioButton(selected = itemValue == selectedValue, onClick = null)
    }
}

@Preview
@Composable
fun RadioButtonPreview() {
    VpnTheme(isDark = true) {
        Surface {
            SettingsRadioItem(
                modifier = Modifier,
                itemValue = true,
                selectedValue = true,
                onSelected = {},
                label = "Radio option",
                description = "Long radio button description. Long radio button description. Long radio button description."
            )
        }
    }
}

@Preview
@Composable
fun SettingTogglePreview() {
    VpnTheme(isDark = true) {
        Surface {
            SettingsToggleItem(
                name = "Toggle option",
                description = "Long toggle description. Long toggle description. Long toggle description. Learn more",
                descriptionAnnotation = ClickableTextAnnotation("Learn more", {}, {}),
                value = true,
                onToggle = {},
            )
        }
    }
}
