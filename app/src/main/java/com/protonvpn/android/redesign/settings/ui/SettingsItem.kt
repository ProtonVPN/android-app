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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.AnnotatedClickableText
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
                modifier = paddingModifier
            )
        } else {
            Text(
                text = description,
                style = ProtonTheme.typography.captionWeak,
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
        modifier.clickable(onClick = onToggle),
        name,
        description,
        subTitle,
        descriptionAnnotation
    ) {
        Switch(
            checked = value,
            onCheckedChange = null,
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
    value: SettingsViewModel.SettingViewState<Boolean>,
    subTitle: String? = null,
    descriptionAnnotation: Pair<String, () -> Unit>? = null,
    onToggle: () -> Unit,
    onRestricted: () -> Unit,
) {
    if (value.isRestricted) {
            BaseSettingsItem(
                modifier.clickable(onClick = onRestricted),
                name,
                description,
                subTitle,
                descriptionAnnotation?.let {
                    ClickableTextAnnotation(
                        annotatedPart = it.first,
                        onAnnotatedClick = it.second,
                        onAnnotatedOutsideClick = onRestricted
                    )
                }
            ) {
                Icon(
                painter = painterResource(id = R.drawable.vpn_plus_badge),
                tint = Color.Unspecified,
                contentDescription = null,
            )
        }
    } else {
        SettingsToggleItem(
            modifier = modifier,
            name = name,
            description = description,
            value = value.value,
            subTitle = subTitle,
            descriptionAnnotation = descriptionAnnotation?.let {
                    ClickableTextAnnotation(
                        annotatedPart = it.first,
                        onAnnotatedClick = it.second,
                        onAnnotatedOutsideClick = onToggle
                    )
                },
            onToggle = onToggle
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
            .clickable(onClick = { onSelected(itemValue) })
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.padding(end = 8.dp).weight(1f),
        ) {
            Text(
                text = label,
                style = ProtonTheme.typography.defaultSmallNorm,
            )
            Text(
                text = description,
                style = ProtonTheme.typography.captionWeak,
            )
        }
        RadioButton(selected = itemValue == selectedValue, onClick = null)
    }
}

@Composable
fun SettingsViewModel.SettingViewState<Boolean>.ToToggle(
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
    onAnnotatedClick: () -> Unit = {},
    onRestricted: () -> Unit = {},
) = SettingsItem(
    modifier = modifier,
    name = stringResource(id = titleRes),
    description = descriptionText(),
    descriptionAnnotation = annotationRes?.let { stringResource(id = it) to onAnnotatedClick },
    value = this,
    onToggle = onToggle,
    onRestricted = onRestricted,
)

@Composable
fun <T> SettingsViewModel.SettingViewState<T>.descriptionText() =
    if (annotationRes != null) {
        stringResource(
            id = descriptionRes,
            stringResource(id = annotationRes)
        )
    } else {
        stringResource(id = descriptionRes)
    }

@Preview
@Composable
fun RadioButtonPreview() {
    VpnTheme(isDark = true) {
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

@Preview
@Composable
fun SettingTogglePreview() {
    VpnTheme(isDark = true) {
        SettingsToggleItem(
            name = "Toggle option",
            description = "Long toggle description. Long toggle description. Long toggle description. Learn more",
            descriptionAnnotation = ClickableTextAnnotation("Learn more", {}, {}),
            value = true,
            onToggle = {},
        )
    }
}