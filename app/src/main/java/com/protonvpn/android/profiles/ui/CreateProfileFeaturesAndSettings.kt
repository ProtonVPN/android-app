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

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.profiles.data.ProfileAutoOpen
import com.protonvpn.android.redesign.settings.ui.NatType
import com.protonvpn.android.settings.data.CustomDnsSettings
import com.protonvpn.android.ui.settings.LabeledItem
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.openUrl
import com.protonvpn.android.vpn.ProtocolSelection
import me.proton.core.compose.theme.ProtonTheme


@Composable
fun CreateProfileFeaturesAndSettingsRoute(
    viewModel: CreateEditProfileViewModel,
    onOpenCustomDns: () -> Unit,
    onOpenLan: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val state = viewModel.settingsScreenStateFlow.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = ProtonTheme.colors.backgroundNorm)
    ) {
        if (state != null) {
            ProfileFeaturesAndSettings(
                state = state,
                onNetShieldChange = viewModel::setNetShield,
                onProtocolChange = viewModel::setProtocol,
                onNatChange = viewModel::setNatType,
                onOpenLan = onOpenLan,
                onAutoOpenChange = viewModel::setAutoOpen,
                onDisableCustomDns = { viewModel.toggleCustomDns() },
                onDisablePrivateDns = { context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) },
                onOpenCustomDns = onOpenCustomDns,
                onCustomDnsLearnMore = { context.openUrl(Constants.URL_NETSHIELD_CUSTOM_DNS_LEARN_MORE) },
                onPrivateDnsLearnMore = { context.openUrl(Constants.URL_CUSTOM_DNS_PRIVATE_DNS_LEARN_MORE) },
                onNext = onNext,
                onBack = onBack,
                getAutoOpenAppInfo = viewModel::getAutoOpenAppInfo,
                getAutoOpenAllAppsInfo = viewModel::getAutoOpenAllAppsInfo,
            )
            LaunchedEffect(Unit) {
                viewModel.settingsScreenShown()
            }
        }
    }
}

@Composable
fun ProfileFeaturesAndSettings(
    state: SettingsScreenState,
    onNetShieldChange: (Boolean) -> Unit,
    onProtocolChange: (ProtocolSelection) -> Unit,
    onNatChange: (NatType) -> Unit,
    onOpenLan: () -> Unit,
    onDisableCustomDns: () -> Unit,
    onDisablePrivateDns: () -> Unit,
    onCustomDnsLearnMore: () -> Unit,
    onPrivateDnsLearnMore: () -> Unit,
    onOpenCustomDns: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onAutoOpenChange: (ProfileAutoOpen) -> Unit,
    getAutoOpenAppInfo: suspend (String) -> LabeledItem?,
    getAutoOpenAllAppsInfo: suspend (Int) -> List<LabeledItem>,
) {
    CreateProfileStep(
        onNext = onNext,
        onBack = onBack,
        onNextText = stringResource(id = R.string.saveButton),
        applyContentHorizontalPadding = false
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = stringResource(id = R.string.configure_profile_features_and_settings_title),
                color = ProtonTheme.colors.textNorm,
                style = ProtonTheme.typography.body1Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = stringResource(id = R.string.configure_profile_features_and_settings_description),
                color = ProtonTheme.colors.textWeak,
                style = ProtonTheme.typography.body2Regular,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            if (state.netShield != null) {
                ProfileNetShieldItem(
                    value = state.netShield,
                    onNetShieldChange = onNetShieldChange,
                    onDisableCustomDns = onDisableCustomDns,
                    onDisablePrivateDns = onDisablePrivateDns,
                    onCustomDnsLearnMore = onCustomDnsLearnMore,
                    onPrivateDnsLearnMore = onPrivateDnsLearnMore,
                    dnsOverride = state.dnsOverride,
                )
            }
            ProfileProtocolItem(
                value = state.protocol,
                onSelect = onProtocolChange,
            )
            ProfileNatItem(
                value = state.natType,
                onSelect = onNatChange,
            )
            ProfileLanConnectionsItem(
                value = state.lanConnections,
                onClick = onOpenLan,
            )
            ProfileAutoOpenItem(
                value = state.autoOpen,
                onChange = onAutoOpenChange,
                isNew = state.isAutoOpenNew,
                showPrivateBrowsing = state.showPrivateBrowsing,
                getAppInfo = getAutoOpenAppInfo,
                getAllAppsInfo = getAutoOpenAllAppsInfo,
            )
            state.customDnsSettings?.let {
                ProfileCustomDnsItem(
                    value = it.effectiveEnabled,
                    onClick = onOpenCustomDns,
                    dnsOverride = state.dnsOverride,
                )
            }
        }
    }
}

@ProtonVpnPreview
@Composable
fun PreviewFeaturesAndSettings() {
    ProtonVpnPreview {
        ProfileFeaturesAndSettings(
            state = SettingsScreenState(
                netShield = true,
                isPrivateDnsActive = false,
                ProtocolSelection(VpnProtocol.WireGuard, null),
                NatType.Strict,
                lanConnections = false,
                lanConnectionsAllowDirect = false,
                autoOpen = ProfileAutoOpen.None,
                customDnsSettings = CustomDnsSettings(false),
                isAutoOpenNew = true,
                showPrivateBrowsing = true,
            ),
            onNetShieldChange = {},
            onProtocolChange = {},
            onNatChange = {},
            onOpenLan = {},
            onNext = {},
            onDisableCustomDns = {},
            onBack = {},
            onOpenCustomDns = {},
            onCustomDnsLearnMore = {},
            onAutoOpenChange = {},
            onDisablePrivateDns = {},
            onPrivateDnsLearnMore = {},
            getAutoOpenAppInfo = { null },
            getAutoOpenAllAppsInfo = { emptyList() },
        )
    }
}
