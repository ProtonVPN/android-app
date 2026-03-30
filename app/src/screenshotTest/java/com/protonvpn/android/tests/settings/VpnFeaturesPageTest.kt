/*
 *
 *  * Copyright (c) 2023. Proton AG
 *  *
 *  * This file is part of ProtonVPN.
 *  *
 *  * ProtonVPN is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * ProtonVPN is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.protonvpn.android.tests.settings

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.protonvpn.android.annotations.ProtonVpnTestPreview
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.redesign.settings.ui.KillSwitchInfo
import com.protonvpn.android.redesign.settings.ui.LanSetting
import com.protonvpn.android.redesign.settings.ui.NetShieldSetting
import com.protonvpn.android.redesign.settings.ui.SettingsViewModel
import com.protonvpn.android.redesign.settings.ui.SplitTunnelingSubSetting
import com.protonvpn.android.redesign.settings.ui.ThemeSettings
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.theme.ThemeType
import com.protonvpn.android.vpn.DnsOverride

@PreviewTest
@ProtonVpnTestPreview
@Composable
fun NetshieldSettingPage() {
    ProtonVpnPreview {
        NetShieldSetting(
            onClose = {},
            onLearnMore = {},
            onPrivateDnsLearnMore = {},
            onOpenPrivateDnsSettings = {},
            onDisableCustomDns = {},
            onCustomDnsLearnMore = {},
            onNetShieldToggle = {},
            onToggleNetShieldAdultContentBlock = {},
            netShield = SettingsViewModel.SettingViewState.NetShield(
                netShieldProtocol = NetShieldProtocol.ENABLED_EXTENDED,
                isRestricted = false,
                profileOverrideInfo = null,
                dnsOverride = DnsOverride.None,
                isNetShieldLevelThreeAvailable = false,
            ),
        )
    }
}

@PreviewTest
@ProtonVpnTestPreview
@Composable
fun LanSettingPage() {
    ProtonVpnPreview {
        LanSetting(
            onClose = {},
            onToggleLan = {},
            onToggleAllowDirectConnection = {},
            lan = SettingsViewModel.SettingViewState.LanConnections(
                value = true,
                allowDirectConnections = false,
                isFreeUser = false,
                overrideProfilePrimaryLabel = null
            )
        )
    }
}

@PreviewTest
@ProtonVpnTestPreview
@Composable
fun NetshieldPrivateDnsSettingPage() {
    ProtonVpnPreview {
        NetShieldSetting(
            onClose = {},
            onLearnMore = {},
            onPrivateDnsLearnMore = {},
            onOpenPrivateDnsSettings = {},
            onDisableCustomDns = {},
            onCustomDnsLearnMore = {},
            onNetShieldToggle = {},
            onToggleNetShieldAdultContentBlock = {},
            netShield = SettingsViewModel.SettingViewState.NetShield(
                netShieldProtocol = NetShieldProtocol.ENABLED_EXTENDED,
                isRestricted = false,
                profileOverrideInfo = null,
                dnsOverride = DnsOverride.SystemPrivateDns,
                isNetShieldLevelThreeAvailable = false,
            ),
        )
    }
}

@PreviewTest
@ProtonVpnTestPreview
@Composable
fun NetshieldCustomDnsSettingPage() {
    ProtonVpnPreview {
        NetShieldSetting(
            onClose = {},
            onLearnMore = {},
            onPrivateDnsLearnMore = {},
            onOpenPrivateDnsSettings = {},
            onDisableCustomDns = {},
            onCustomDnsLearnMore = {},
            onNetShieldToggle = {},
            onToggleNetShieldAdultContentBlock = {},
            netShield = SettingsViewModel.SettingViewState.NetShield(
                netShieldProtocol = NetShieldProtocol.ENABLED_EXTENDED,
                isRestricted = false,
                profileOverrideInfo = null,
                dnsOverride = DnsOverride.CustomDns,
                isNetShieldLevelThreeAvailable = false,
            ),
        )
    }
}

@ProtonVpnTestPreview
@Composable
fun NetShieldAdultContentBlockSettingPage() {
    ProtonVpnPreview {
        NetShieldSetting(
            onClose = {},
            onLearnMore = {},
            onPrivateDnsLearnMore = {},
            onOpenPrivateDnsSettings = {},
            onDisableCustomDns = {},
            onCustomDnsLearnMore = {},
            onNetShieldToggle = {},
            onToggleNetShieldAdultContentBlock = {},
            netShield = SettingsViewModel.SettingViewState.NetShield(
                netShieldProtocol = NetShieldProtocol.ENABLED_EXTENDED_ADULT_CONTENT,
                isRestricted = false,
                profileOverrideInfo = null,
                dnsOverride = DnsOverride.None,
                isNetShieldLevelThreeAvailable = true,
            ),
        )
    }
}

@PreviewTest
@ProtonVpnTestPreview
@Composable
fun SplitTunnelingPage() {
    ProtonVpnPreview {
        SplitTunnelingSubSetting(
            onClose = {},
            onLearnMore = {},
            onSplitTunnelToggle = {},
            onIpsClick = {},
            onAppsClick = {},
            onSplitTunnelModeSelected = {},
            splitTunneling = SettingsViewModel.SettingViewState.SplitTunneling(
                isEnabled = true,
                mode = SplitTunnelingMode.EXCLUDE_ONLY,
                currentModeAppNames = listOf(),
                currentModeIps = listOf(
                    "192.158.254.584",
                    "2001:db8:3333:4444:5555:6666:7777:8888",
                    "158.254.254.214",
                    "1.1.1.1"
                    ),
                isFreeUser = false
            )
        )
    }
}

@PreviewTest
@ProtonVpnTestPreview
@Composable
fun KillSwitchPage() {
    ProtonVpnPreview {
        KillSwitchInfo(
            onClose = {},
            onLearnMore = {},
            onOpenVpnSettings = {}
        )
    }
}

@PreviewTest
@ProtonVpnTestPreview
@Composable
fun ThemePage() {
    ProtonVpnPreview {
        ThemeSettings(ThemeType.Dark, {}, {})
    }
}
