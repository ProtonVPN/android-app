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
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.protonvpn.android.R
import com.protonvpn.android.redesign.base.ui.SettingsItem
import com.protonvpn.android.redesign.base.ui.ClickableTextAnnotation
import com.protonvpn.android.redesign.base.ui.SettingsToggleItem

@Composable
private fun SettingsToggleWithRestrictions(
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
        SettingsItem(
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
fun SettingsViewModel.SettingViewState<Boolean>.ToToggle(
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
    onAnnotatedClick: () -> Unit = {},
    onRestricted: () -> Unit = {},
) = SettingsToggleWithRestrictions(
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
