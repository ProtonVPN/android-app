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
import androidx.compose.ui.tooling.preview.Preview
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.redesign.settings.ui.NatType
import com.protonvpn.android.redesign.settings.ui.SettingsView
import com.protonvpn.android.redesign.settings.ui.SettingsViewModel
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.vpn.ProtocolSelection
import me.proton.core.accountmanager.presentation.compose.viewmodel.AccountSettingsViewState
import me.proton.core.domain.entity.UserId

@Composable
@Preview(heightDp = 1625)
fun SettingsCredentialless() {
    val settings = SettingsData(true)

    ProtonVpnPreview(addSurface = false) {
        SettingsView(accountSettingsViewState = settings.credentialessAccountViewState,
            viewState = settings.settingsViewState,
            onVpnAcceleratorClick = {},
            onAccountClick = {},
            onProtocolClick = {},
            onVpnAcceleratorUpgrade = {},
            onNotificationsClick = {},
            onRateUsClick = {},
            onSignInClick = {},
            onSignUpClick = {},
            onSignOutClick = {},
            onOnHelpCenterClick = {},
            onDebugLogsClick = {},
            onHelpFightClick = {},
            onNetShieldClick = {},
            onReportBugClick = {},
            onSplitTunnelUpgrade = {},
            onIconChangeClick = {},
            onSplitTunnelClick = {},
            onAlwaysOnClick = {},
            onNetShieldUpgradeClick = {},
            onThirdPartyLicensesClick = {},
            onDefaultConnectionClick = {},
            onAdvancedSettingsClick = {},
            onDebugToolsClick = {})
    }
}

private class SettingsData(isFree: Boolean) {
    private val netshield = SettingsViewModel.SettingViewState.NetShield(true, isFree)
    private val splitTunneling = SettingsViewModel.SettingViewState.SplitTunneling(
        true, SplitTunnelingMode.EXCLUDE_ONLY, listOf(), listOf(), isFree
    )
    private val vpnAccelerator = SettingsViewModel.SettingViewState.VpnAccelerator(true, isFree)
    private val protocol = SettingsViewModel.SettingViewState.Protocol(ProtocolSelection.SMART)
    private val altRouting = SettingsViewModel.SettingViewState.AltRouting(true)
    private val lanConnections = SettingsViewModel.SettingViewState.LanConnections(true, isFree)
    private val natType = SettingsViewModel.SettingViewState.Nat(NatType.Strict, isFree)

    val settingsViewState = SettingsViewModel.SettingsViewState(
        netShield = netshield,
        splitTunneling = splitTunneling,
        vpnAccelerator = vpnAccelerator,
        showSignOut = false,
        accountScreenEnabled = true,
        protocol = protocol,
        altRouting = altRouting,
        buildInfo = null,
        lanConnections = lanConnections,
        natType = natType,
        versionName = "1.2.3.4",
        showDebugTools = false
    )

    val credentialessAccountViewState = AccountSettingsViewState.CredentialLess(UserId("test"))
}
