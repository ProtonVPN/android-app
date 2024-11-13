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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.protonvpn.android.base.ui.AnnotatedClickableText
import com.protonvpn.android.base.ui.ProtonSwitch
import com.protonvpn.android.base.ui.theme.VpnTheme
import com.protonvpn.android.redesign.settings.ui.SettingValue
import com.protonvpn.android.redesign.settings.ui.SettingValueView
import me.proton.core.compose.component.VerticalSpacer
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultNorm

data class ClickableTextAnnotation(
    val annotatedPart: String,
    val onAnnotatedClick: () -> Unit,
    val onAnnotatedOutsideClick: () -> Unit,
)

@Composable
fun SettingsItem(
    modifier: Modifier = Modifier,
    name: String,
    description: String? = null,
    subTitle: String? = null,
    descriptionAnnotation: ClickableTextAnnotation? = null,
    actionComposable: @Composable () -> Unit
) {
    SettingsItem(modifier, name, description,
        SettingValue.SettingText(subTitle), descriptionAnnotation, actionComposable)
}

@Composable
fun SettingsItem(
    modifier: Modifier = Modifier,
    name: String,
    description: String? = null,
    settingsValue: SettingValue?,
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
        if (settingsValue != null) {
            SettingValueView(settingValue = settingsValue)
        }
        val paddingModifier = Modifier.padding(end = 8.dp, top = 16.dp)
        if (description != null) {
            if (descriptionAnnotation != null) {
                AnnotatedClickableText(
                    fullText = description,
                    annotatedPart = descriptionAnnotation.annotatedPart,
                    onAnnotatedClick = descriptionAnnotation.onAnnotatedClick,
                    onAnnotatedOutsideClick = descriptionAnnotation.onAnnotatedOutsideClick,
                    style = ProtonTheme.typography.body2Regular,
                    color = ProtonTheme.colors.textWeak,
                    modifier = paddingModifier
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
}

@Composable
fun SettingsToggleItem(
    modifier: Modifier = Modifier,
    name: String,
    description: String?,
    value: Boolean,
    settingsValue: SettingValue? = null,
    descriptionAnnotation: ClickableTextAnnotation? = null,
    onToggle: () -> Unit
) {
    SettingsItem(
        modifier.toggleable(value, onValueChange = { onToggle() }),
        name = name,
        description = description,
        settingsValue = settingsValue,
        descriptionAnnotation = descriptionAnnotation
    ) {
        // Do not show switch for overriden values
        if (settingsValue !is SettingValue.SettingOverrideValue) {
            ProtonSwitch(
                checked = value,
                onCheckedChange = null,
            )
        }
    }
}

@Composable
fun SettingsRadioItemSmall(
    title: String,
    description: String?,
    selected: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier,
    titleColor: Color = Color.Unspecified,
    horizontalContentPadding: Dp = 0.dp,
    trailingTitleContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .selectable(selected, onClick = onSelected)
            .padding(vertical = 12.dp, horizontal = horizontalContentPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingContent != null) {
            leadingContent()
        }
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = ProtonTheme.typography.body2Regular,
                    color = titleColor,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (trailingTitleContent != null) {
                    Spacer(Modifier.width(8.dp))
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
            modifier = Modifier
                .clearAndSetSemantics {}
                .padding(start = 8.dp)
        )
    }
}

@Preview
@Composable
fun RadioButtonPreview() {
    VpnTheme(isDark = true) {
        Surface {
            SettingsRadioItemSmall(
                title = "Radio option",
                description = "Long radio button description. Long radio button description. Long radio button description.",
                selected = true,
                onSelected = {},
                horizontalContentPadding = 16.dp,
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
