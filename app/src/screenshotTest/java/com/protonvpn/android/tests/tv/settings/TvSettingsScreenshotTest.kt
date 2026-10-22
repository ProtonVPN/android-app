/*
 * Copyright (c) 2026. Proton AG
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

package com.protonvpn.android.tests.tv.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.tools.screenshot.PreviewTest
import com.protonvpn.android.R
import com.protonvpn.android.annotations.ProtonVpnTvTestPreview
import com.protonvpn.android.base.ui.ProtonVpnTvPreview
import com.protonvpn.android.tv.main_screen.ui.CollapsedDrawerWidth
import com.protonvpn.android.tv.settings.main.ui.TvSetting
import com.protonvpn.android.tv.settings.main.ui.TvSettingItem
import com.protonvpn.android.tv.settings.main.ui.TvSettingValue
import com.protonvpn.android.tv.settings.main.ui.TvSettings
import com.protonvpn.android.tv.settings.main.ui.TvSettingsViewState

@PreviewTest
@ProtonVpnTvTestPreview
@Composable
fun TvSettings_PlusUser_Screenshot() {
    TvSettingsTest(
        TvSettingsViewState(
            settings = listOf(
                TvSettingItem(TvSetting.NetShield, TvSettingValue.Text(R.string.netshield_state_on), null),
                TvSettingItem(TvSetting.SplitTunneling, TvSettingValue.Text(R.string.split_tunneling_state_off), null),
                TvSettingItem(TvSetting.Protocol, TvSettingValue.Text(R.string.settingsProtocolNameSmart), null),
                TvSettingItem(TvSetting.AutoConnect, TvSettingValue.Text(R.string.auto_connect_state_off), null),
                TvSettingItem(TvSetting.Lan, TvSettingValue.Text(R.string.lan_state_off), null),
                TvSettingItem(TvSetting.CustomDns, TvSettingValue.Text(R.string.custom_dns_state_off), null),
                TvSettingItem(TvSetting.IPv6, TvSettingValue.Text(R.string.ipv6_state_on), null),
                TvSettingItem(TvSetting.ReportIssue, TvSettingValue.None, null),
                TvSettingItem(TvSetting.SignOut, TvSettingValue.None, null),
            ),
            accountEmail = "user@proton.me",
            planName = "VPN Plus",
            appVersion = "1.2.3",
        )
    )
}

@PreviewTest
@ProtonVpnTvTestPreview
@Composable
fun TvSettings_FreeUser_Screenshot() {
    TvSettingsTest(
        TvSettingsViewState(
            settings = listOf(
                TvSettingItem(TvSetting.NetShield, TvSettingValue.Restricted, null),
                TvSettingItem(TvSetting.SplitTunneling, TvSettingValue.Restricted, null),
                TvSettingItem(TvSetting.Protocol, TvSettingValue.Text(R.string.settingsProtocolNameSmart), null),
                TvSettingItem(TvSetting.AutoConnect, TvSettingValue.Text(R.string.auto_connect_state_off), null),
                TvSettingItem(TvSetting.Lan, TvSettingValue.Restricted, null),
                TvSettingItem(TvSetting.CustomDns, TvSettingValue.Restricted, null),
                TvSettingItem(TvSetting.IPv6, TvSettingValue.Text(R.string.ipv6_state_on), null),
                TvSettingItem(TvSetting.ReportIssue, TvSettingValue.None, null),
                TvSettingItem(TvSetting.SignOut, TvSettingValue.None, null),
            ),
            accountEmail = "user@proton.me",
            planName = null,
            appVersion = "1.2.3",
        )
    )
}

@Composable
private fun TvSettingsTest(viewState: TvSettingsViewState) {
    ProtonVpnTvPreview {
        Row(Modifier.fillMaxSize()) {
            // Placeholder for the collapsed navigation drawer.
            Box(
                Modifier
                    .width(CollapsedDrawerWidth)
                    .fillMaxHeight()
            )
            // Same padding as in TvMainScreenNav.
            TvSettings(
                viewState = viewState,
                onClick = {},
                modifier = Modifier
                    .weight(1f)
                    .padding(end = CollapsedDrawerWidth)
            )
        }
    }
}
