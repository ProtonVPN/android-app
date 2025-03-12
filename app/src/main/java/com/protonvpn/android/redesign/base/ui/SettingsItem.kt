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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.AnnotatedClickableText
import com.protonvpn.android.base.ui.ProtonSwitch
import com.protonvpn.android.base.ui.theme.VpnTheme
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
    title: String,
    modifier: Modifier = Modifier,
    titleTrailing: (@Composable RowScope.() -> Unit)? = null,
    subtitle: (@Composable () -> Unit)? = null,
    description: (@Composable () -> Unit)? = null,
) {
    SettingsItemScaffold(
        titleRow = {
            Text(title, modifier = Modifier.weight(1f))
            if (titleTrailing != null)
                titleTrailing()
        },
        modifier = modifier,
        subtitle = subtitle,
        description = description,
    )
}

@Composable
fun SettingsItemScaffold(
    titleRow: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    subtitle: (@Composable () -> Unit)? = null,
    description: (@Composable () -> Unit)? = null,
) {
    Column(modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProvideTextStyle(ProtonTheme.typography.body1Regular) {
                titleRow()
            }
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
        name,
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
    SettingsItemScaffold(
        name,
        modifier = itemModifier,
        titleTrailing = {
            when {
                needsUpgrade -> IconNeedsUpgrade()
                settingValue != null -> { InlineSettingValue(settingValue) }
            }
        },
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
) {
    val itemModifier = if (needsUpgrade && onUpgrade != null) {
        modifier.clickable(onClick = onUpgrade)
    } else {
        modifier.toggleable(value, onValueChange = { onToggle() })
    }
    SettingsItemScaffold(
        title = name,
        modifier = itemModifier,
        titleTrailing = {
            when {
                needsUpgrade -> IconNeedsUpgrade()
                settingsValue is SettingValue.SettingOverrideValue -> OverrideSettingLabel(settingsValue)
                else -> ProtonSwitch(checked = value, onCheckedChange = null)
            }
        },
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

@Composable
private fun RowScope.InlineSettingValue(
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
