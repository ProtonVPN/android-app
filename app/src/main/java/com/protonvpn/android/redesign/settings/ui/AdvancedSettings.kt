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

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.redesign.base.ui.ClickableTextAnnotation
import com.protonvpn.android.redesign.base.ui.SettingsValueItem

@Composable
fun AdvancedSettings(
    onClose: () -> Unit,
    profileOverrideInfo: SettingsViewModel.ProfileOverrideInfo?,
    altRouting: SettingsViewModel.SettingViewState.AltRouting,
    allowLan: SettingsViewModel.SettingViewState.LanConnections,
    ipV6: SettingsViewModel.SettingViewState.IPv6?,
    natType: SettingsViewModel.SettingViewState.Nat,
    onAltRoutingChange: () -> Unit,
    onAllowLanChange: () -> Unit,
    onIPv6Toggle: () -> Unit,
    onNatTypeLearnMore: () -> Unit,
    onNavigateToNatType: () -> Unit,
    onAllowLanRestricted: () -> Unit,
    onNatTypeRestricted: () -> Unit,
    onIPv6InfoClick: () -> Unit,
) {
    SubSetting(
        title = stringResource(id = R.string.settings_advanced_settings_title),
        onClose = onClose
    ) {
        profileOverrideInfo?.let {
            ProfileOverrideView(
                modifier = Modifier.padding(16.dp),
                profileOverrideInfo = it
            )
        }

        SettingsToggleItem(
            altRouting,
            onToggle = onAltRoutingChange
        )
        SettingsToggleItem(
            allowLan,
            onToggle = onAllowLanChange,
            onRestricted = onAllowLanRestricted
        )
        SettingsNatItem(
            natType,
            onNatTypeLearnMore = onNatTypeLearnMore,
            onNavigateToNatType = onNavigateToNatType,
            onNatTypeRestricted = onNatTypeRestricted
        )
        if (ipV6 != null) {
            SettingsToggleItem(
                ipV6,
                onToggle = onIPv6Toggle,
                onInfoClick = onIPv6InfoClick,
            )
        }
    }
}

@Composable
private fun SettingsNatItem(
    setting: SettingsViewModel.SettingViewState.Nat,
    onNatTypeLearnMore: () -> Unit,
    onNavigateToNatType: () -> Unit,
    onNatTypeRestricted: () -> Unit,
) = with(setting) {
    val natOnClick = if (isRestricted) onNatTypeRestricted else onNavigateToNatType
    SettingsValueItem(
        onClick = natOnClick,
        onUpgrade = onNatTypeRestricted,
        name = stringResource(id = titleRes),
        description = descriptionText(),
        needsUpgrade = isRestricted,
        settingValue = settingValueView,
        descriptionAnnotation = annotationRes?.let {
            ClickableTextAnnotation(
                annotatedPart = stringResource(id = it),
                onAnnotatedClick = onNatTypeLearnMore,
                onAnnotatedOutsideClick = natOnClick,
            )
        },
    )
}
