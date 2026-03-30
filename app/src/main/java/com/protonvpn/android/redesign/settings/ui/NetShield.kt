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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.base.ui.SettingsFeatureToggle
import com.protonvpn.android.base.ui.largeScreenContentPadding
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.vpn.DnsOverride
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun NetShieldSetting(
    onClose: () -> Unit,
    netShield: SettingsViewModel.SettingViewState.NetShield,
    onLearnMore: () -> Unit,
    onNetShieldToggle: () -> Unit,
    onDisableCustomDns: () -> Unit,
    onCustomDnsLearnMore: () -> Unit,
    onPrivateDnsLearnMore: () -> Unit,
    onOpenPrivateDnsSettings: () -> Unit,
    onToggleNetShieldAdultContentBlock: () -> Unit,
) {
    val listState = rememberLazyListState()
    FeatureSubSettingScaffold(
        title = stringResource(id = netShield.titleRes),
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
                setting = netShield,
                imageRes = R.drawable.setting_netshield,
                onLearnMore = onLearnMore,
                itemModifier = horizontalItemPaddingModifier,
            )
            item {
                when (netShield.dnsOverride) {
                    DnsOverride.None -> SettingsFeatureToggle(
                        label = stringResource(netShield.titleRes),
                        checked = netShield.value,
                        onCheckedChange = { _ -> onNetShieldToggle() },
                        modifier = horizontalItemPaddingModifier.padding(top = 16.dp)
                    )

                    DnsOverride.CustomDns -> DnsConflictBanner(
                        titleRes = R.string.custom_dns_conflict_banner_netshield_title,
                        descriptionRes = R.string.custom_dns_conflict_banner_netshield_description,
                        buttonRes = R.string.custom_dns_conflict_banner_disable_custom_dns_button,
                        onLearnMore = onCustomDnsLearnMore,
                        onButtonClicked = onDisableCustomDns,
                        modifier = horizontalItemPaddingModifier.padding(top = 24.dp),
                    )

                    DnsOverride.SystemPrivateDns -> DnsConflictBanner(
                        titleRes = R.string.private_dns_conflict_banner_netshield_title,
                        descriptionRes = R.string.private_dns_conflict_banner_netshield_description,
                        buttonRes = R.string.private_dns_conflict_banner_network_settings_button,
                        onLearnMore = onPrivateDnsLearnMore,
                        onButtonClicked = onOpenPrivateDnsSettings,
                        modifier = horizontalItemPaddingModifier.padding(top = 24.dp),
                    )
                }
            }
            item {
                Text(
                    text = stringResource(id = R.string.netshield_setting_warning),
                    style = ProtonTheme.typography.body2Regular,
                    color = ProtonTheme.colors.textWeak,
                    modifier = horizontalItemPaddingModifier.padding(top = 16.dp),
                )
            }

            item {
                AnimatedVisibility(
                    visible = netShield.isAdultContentBlockAvailable,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    SettingsCheckbox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        title = stringResource(id = R.string.netshield_setting_block_adult_content),
                        value = netShield.isAdultContentBlocked,
                        onValueChange = { _ -> onToggleNetShieldAdultContentBlock() },
                    )
                }
            }
        }
    }
}

@ProtonVpnPreview
@Composable
private fun NetShieldSettingPreview(
    @PreviewParameter(PreviewSettingNetShieldViewStateProvider::class)
    netShield: SettingsViewModel.SettingViewState.NetShield,
) {
    ProtonVpnPreview {
        NetShieldSetting(
            onClose = {},
            netShield = netShield,
            onLearnMore = {},
            onNetShieldToggle = {},
            onDisableCustomDns = {},
            onCustomDnsLearnMore = {},
            onPrivateDnsLearnMore = {},
            onOpenPrivateDnsSettings = {},
            onToggleNetShieldAdultContentBlock = {},
        )
    }

}

private class PreviewSettingNetShieldViewStateProvider :
    PreviewParameterProvider<SettingsViewModel.SettingViewState.NetShield> {

    override val values: Sequence<SettingsViewModel.SettingViewState.NetShield> = sequenceOf(
        SettingsViewModel.SettingViewState.NetShield(
            netShieldProtocol = NetShieldProtocol.DISABLED,
            isRestricted = false,
            profileOverrideInfo = null,
            dnsOverride = DnsOverride.None,
            isNetShieldLevelThreeAvailable = true,
        ),
        SettingsViewModel.SettingViewState.NetShield(
            netShieldProtocol = NetShieldProtocol.ENABLED_EXTENDED,
            isRestricted = true,
            profileOverrideInfo = null,
            dnsOverride = DnsOverride.None,
            isNetShieldLevelThreeAvailable = false,
        ),
        SettingsViewModel.SettingViewState.NetShield(
            netShieldProtocol = NetShieldProtocol.ENABLED_EXTENDED,
            isRestricted = false,
            profileOverrideInfo = null,
            dnsOverride = DnsOverride.None,
            isNetShieldLevelThreeAvailable = true,
        ),
        SettingsViewModel.SettingViewState.NetShield(
            netShieldProtocol = NetShieldProtocol.ENABLED_EXTENDED_ADULT_CONTENT,
            isRestricted = false,
            profileOverrideInfo = null,
            dnsOverride = DnsOverride.None,
            isNetShieldLevelThreeAvailable = true,
        ),
    )

}
