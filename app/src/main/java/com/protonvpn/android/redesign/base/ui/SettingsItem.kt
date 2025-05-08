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

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.AnnotatedClickableText
import com.protonvpn.android.base.ui.ProtonSwitch
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.redesign.settings.ui.OverrideSettingLabel
import com.protonvpn.android.redesign.settings.ui.SettingValue
import me.proton.core.compose.component.VerticalSpacer
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultWeak
import me.proton.core.presentation.R as CoreR

data class ClickableTextAnnotation(
    val annotatedPart: String,
    val onAnnotatedClick: () -> Unit,
    val onAnnotatedOutsideClick: () -> Unit,
)

@Composable
fun SettingsItemScaffold(
    titleRow: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: (@Composable () -> Unit)? = null,
    description: (@Composable () -> Unit)? = null,
) {
    Column(modifier.padding(16.dp)) {
        ProvideTextStyle(ProtonTheme.typography.body1Regular) {
            titleRow()
        }
        if (subtitle != null) {
            ProvideTextStyle(ProtonTheme.typography.defaultWeak) {
                VerticalSpacer(height = 4.dp)
                subtitle()
            }
        }
        CompositionLocalProvider(
            LocalTextStyle provides ProtonTheme.typography.body2Regular,
            LocalContentColor provides ProtonTheme.colors.textWeak
        ) {
            if (description != null) {
                VerticalSpacer(height = 16.dp)
                description()
            }
        }
    }
}

@Composable
fun SettingsItem(
    modifier: Modifier = Modifier,
    name: String,
    description: String? = null,
    subTitle: String? = null,
    descriptionAnnotation: ClickableTextAnnotation? = null,
) {
    SettingsItemScaffold(
        titleRow = { SettingItemTitleRow(name) },
        modifier = modifier,
        subtitle = subTitle?.let { { Text(subTitle) } },
        description = description?.let {
            { SettingDescription(it, descriptionAnnotation, modifier = Modifier.padding(end = 8.dp)) }
        }
    )
}

@Composable
fun SettingsValueItem(
    name: String,
    settingValue: SettingValue?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    descriptionAnnotation: ClickableTextAnnotation? = null,
    needsUpgrade: Boolean = false,
    onUpgrade: (() -> Unit)? = null,
) {
    val itemModifier = modifier.clickable(onClick = if (needsUpgrade && onUpgrade != null) onUpgrade else onClick)
    val settingValueView = @Composable {
        when {
            needsUpgrade -> IconNeedsUpgrade()
            settingValue != null -> { InlineSettingValue(settingValue) }
        }
    }
    SettingsItemScaffold(
        titleRow = { SettingItemTitleRow(name, trailingContent = settingValueView) },
        modifier = itemModifier,
        description = description?.let {
            { SettingDescription(it, descriptionAnnotation, modifier = Modifier.padding(end = 8.dp)) }
        }
    )
}

@Composable
fun SettingsToggleItem(
    modifier: Modifier = Modifier,
    name: String,
    description: String?,
    value: Boolean,
    onToggle: () -> Unit,
    needsUpgrade: Boolean = false,
    settingsValue: SettingValue? = null, // Needed only for override, value is passed as "value". Simplify this.
    descriptionAnnotation: ClickableTextAnnotation? = null,
    onUpgrade: (() -> Unit)? = null,
    onInfoClick: (() -> Unit)? = null,
) {
    val itemModifier = if (needsUpgrade && onUpgrade != null) {
        modifier.clickable(onClick = onUpgrade)
    } else {
        modifier.toggleable(value, onValueChange = { onToggle() })
    }
    val trailingContent = @Composable {
        when {
            needsUpgrade -> IconNeedsUpgrade()
            settingsValue is SettingValue.SettingOverrideValue -> OverrideSettingLabel(settingsValue)
            else -> ProtonSwitch(checked = value, onCheckedChange = null)
        }
    }
    SettingsItemScaffold(
        titleRow = { SettingItemTitleRow(name, onInfoClick = onInfoClick, trailingContent = trailingContent) },
        modifier = itemModifier,
        description = description?.let {
            { SettingDescription(it, descriptionAnnotation, modifier = Modifier.padding(end = 8.dp)) }
        }
    )
}

@Composable
fun SettingDescription(
    description: String,
    descriptionAnnotation: ClickableTextAnnotation?,
    modifier: Modifier = Modifier,
) {
    if (descriptionAnnotation != null) {
        AnnotatedClickableText(
            fullText = description,
            annotatedPart = descriptionAnnotation.annotatedPart,
            onAnnotatedClick = descriptionAnnotation.onAnnotatedClick,
            onAnnotatedOutsideClick = descriptionAnnotation.onAnnotatedOutsideClick,
            style = ProtonTheme.typography.body2Regular,
            annotatedStyle = ProtonTheme.typography.body2Medium,
            modifier = modifier
        )
    } else {
        Text(
            text = description,
            modifier = modifier
        )
    }
}

@Composable
private fun SettingItemTitleRow(
    title: String,
    modifier: Modifier = Modifier,
    onInfoClick: (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f, fill = false))
            if (onInfoClick != null) {
                Icon(
                    painterResource(CoreR.drawable.ic_proton_info_circle_filled),
                    contentDescription = null,
                    tint = ProtonTheme.colors.iconWeak,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onInfoClick() }
                        .padding(8.dp)
                        .size(16.dp)
                )
            }
        }
        if (trailingContent != null) {
            trailingContent()
        }
    }
}

@Composable
private fun InlineSettingValue(
    settingValue: SettingValue,
    modifier: Modifier = Modifier
) {
    val Chevron = @Composable {
        Icon(
            painterResource(CoreR.drawable.ic_proton_chevron_right),
            contentDescription = null,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(16.dp)
        )
    }
    Row(modifier = modifier) {
        CompositionLocalProvider(
            LocalContentColor provides ProtonTheme.colors.textWeak,
            LocalTextStyle provides ProtonTheme.typography.body2Regular
        ) {
            when (settingValue) {
                is SettingValue.SettingOverrideValue ->
                    OverrideSettingLabel(settingValue = settingValue, modifier = modifier)

                is SettingValue.SettingStringRes -> {
                    Text(text = stringResource(settingValue.subtitleRes), modifier = modifier)
                    Chevron()
                }

                is SettingValue.SettingText -> {
                    Text(text = settingValue.text, modifier = modifier)
                    Chevron()
                }
            }
        }
    }
}


@Composable
private fun IconNeedsUpgrade(
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(id = R.drawable.vpn_plus_badge),
        contentDescription = null,
        modifier = modifier,
    )
}

@ProtonVpnPreview
@Composable
fun SettingTogglePreview() {
    ProtonVpnPreview {
        Surface {
            SettingsToggleItem(
                name = "Toggle option",
                description = "Long toggle description. Long toggle description. Long toggle description. Learn more",
                value = true,
                onToggle = {},
                descriptionAnnotation = ClickableTextAnnotation("Learn more", {}, {}),
            )
        }
    }
}

@ProtonVpnPreview
@Composable
fun SettingToggleInfoPreview() {
    ProtonVpnPreview {
        Surface {
            SettingsToggleItem(
                name = "Toggle option with a long description that wraps",
                description = "Long toggle description. Long toggle description. Long toggle description. Learn more",
                value = true,
                onToggle = {},
                descriptionAnnotation = ClickableTextAnnotation("Learn more", {}, {}),
                onInfoClick = {},
            )
        }
    }
}

@ProtonVpnPreview
@Composable
fun SettingValuePreview() {
    ProtonVpnPreview {
        Surface {
            SettingsValueItem(
                name = "Setting name",
                description = "Long toggle description. Long toggle description. Long toggle description. Learn more",
                settingValue = SettingValue.SettingText("Current value"),
                descriptionAnnotation = ClickableTextAnnotation("Learn more", {}, {}),
                onClick = {},
            )
        }
    }
}
