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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.redesign.base.ui.largeScreenContentPadding

@Composable
fun LanSetting(
    onClose: () -> Unit,
    lan: SettingsViewModel.SettingViewState.LanConnections,
    onToggleLan: () -> Unit,
    onToggleAllowDirectConnection: () -> Unit,
) {
    val listState = rememberLazyListState()
    FeatureSubSettingScaffold(
        title = stringResource(id = lan.titleRes),
        onClose = onClose,
        listState = listState,
        titleInListIndex = 1,
    ) { contentPadding ->
        val horizontalItemPaddingModifier = Modifier
            .padding(horizontal = 16.dp)
            .largeScreenContentPadding()
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(contentPadding)
        ) {
            addFeatureSettingItems(
                setting = lan,
                imageRes = R.drawable.setting_lan,
                onLearnMore = {},
                onToggle = onToggleLan,
                itemModifier = horizontalItemPaddingModifier,
            )
            if (lan.value && lan.allowDirectConnections != null) {
                item {
                    SettingsCheckbox(
                        title = stringResource(id = R.string.settings_lan_allow_direct_connection_title),
                        description = stringResource(id = R.string.settings_lan_allow_direct_connection_description),
                        value = lan.allowDirectConnections,
                        onValueChange = { onToggleAllowDirectConnection() },
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun LanSettingPreview() {
    ProtonVpnPreview {
        LanSetting(
            onClose = {},
            lan = SettingsViewModel.SettingViewState.LanConnections(
                true,
                allowDirectConnections = false,
                isFreeUser = false,
                overrideProfilePrimaryLabel = null
            ),
            onToggleLan = {},
            onToggleAllowDirectConnection = {},
        )
    }
}