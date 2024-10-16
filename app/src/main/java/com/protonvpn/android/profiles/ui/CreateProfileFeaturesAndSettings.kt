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
package com.protonvpn.android.profiles.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.theme.VpnTheme
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.redesign.base.ui.SettingsToggleItem
import com.protonvpn.android.redesign.settings.ui.NatType
import com.protonvpn.android.vpn.ProtocolSelection
import me.proton.core.compose.theme.ProtonTheme


@Composable
fun CreateProfileFeaturesAndSettingsRoute(
    viewModel: CreateEditProfileViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val state = viewModel.settingsScreenStateFlow.collectAsStateWithLifecycle().value
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = ProtonTheme.colors.backgroundNorm)
    ) {
        if (state != null)
            ProfileFeaturesAndSettings(
                state = state,
                onNetShieldChange = viewModel::setNetShield,
                onProtocolChange = viewModel::setProtocol,
                onNatChange = viewModel::setNatType,
                onLanChange = viewModel::setLanConnections,
                onNext = onNext,
                onBack = onBack
            )
    }
}

@Composable
fun ProfileFeaturesAndSettings(
    state: SettingsScreenState,
    onNetShieldChange: (Boolean) -> Unit,
    onProtocolChange: (ProtocolSelection) -> Unit,
    onNatChange: (NatType) -> Unit,
    onLanChange: (Boolean) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    CreateProfileStep(
        onNext = onNext,
        onBack = onBack,
        onNextText = stringResource(id = R.string.create_profile_button_done)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = stringResource(id = R.string.create_profile_features_and_settings_title),
                color = ProtonTheme.colors.textNorm,
                style = ProtonTheme.typography.body1Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(id = R.string.create_profile_features_and_settings_description),
                color = ProtonTheme.colors.textWeak,
                style = ProtonTheme.typography.body2Regular,
            )
            Spacer(modifier = Modifier.height(8.dp))
            ProfileNetShieldItem(
                value = state.netShield,
                onNetShieldChange = onNetShieldChange,
            )
            ProfileProtocolItem(
                value = state.protocol,
                onSelect = onProtocolChange,
            )
            ProfileNatItem(
                value = state.natType,
                onSelect = onNatChange,
            )
        }

        SettingsToggleItem(
            name = stringResource(id = R.string.settings_advanced_allow_lan_title),
            description = null,
            value = state.lanConnections,
            onToggle = { onLanChange(!state.lanConnections) }
        )
    }
}

@Preview
@Composable
fun PreviewFeaturesAndSettings() {
    VpnTheme(isDark = true) {
        ProfileFeaturesAndSettings(
            state = SettingsScreenState(
                netShield = true,
                ProtocolSelection(VpnProtocol.WireGuard, null),
                NatType.Strict,
                false
            ),
            onNatChange = {},
            onLanChange = {},
            onNetShieldChange = {},
            onProtocolChange = {},
            onBack = {},
            onNext = {}
        )
    }
}