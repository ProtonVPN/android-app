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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.protonvpn.android.redesign.base.ui.ClickableTextAnnotation
import com.protonvpn.android.redesign.base.ui.SettingsToggleItem

@Composable
fun SettingsToggleItem(
    setting: SettingsViewModel.SettingViewState<Boolean>,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onAnnotatedClick: () -> Unit = {},
    onRestricted: () -> Unit = {},
    onInfoClick: (() -> Unit)? = null,
) = with(setting) {
    SettingsToggleItem(
        modifier = modifier,
        name = stringResource(id = titleRes),
        description = descriptionText(),
        value = value,
        settingsValue = settingValueView,
        needsUpgrade = isRestricted,
        descriptionAnnotation = annotationRes?.let {
            ClickableTextAnnotation(
                annotatedPart = stringResource(it),
                onAnnotatedClick = onAnnotatedClick,
                onAnnotatedOutsideClick = onToggle
            )
        },
        onToggle = onToggle,
        onUpgrade = onRestricted,
        onInfoClick = onInfoClick,
    )
}

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
