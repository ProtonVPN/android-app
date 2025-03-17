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
import com.protonvpn.android.profiles.data.ProfileColor
import com.protonvpn.android.profiles.data.ProfileIcon
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.settings.ui.AdvancedSettings
import com.protonvpn.android.redesign.settings.ui.NatType
import com.protonvpn.android.redesign.settings.ui.SettingsView
import com.protonvpn.android.redesign.settings.ui.SettingsViewModel
import com.protonvpn.android.redesign.settings.ui.SettingsViewModel.SettingViewState
import com.protonvpn.android.redesign.settings.ui.customdns.AddDnsError
import com.protonvpn.android.redesign.settings.ui.customdns.AddDnsResult
import com.protonvpn.android.redesign.settings.ui.customdns.AddNewDnsScreen
import com.protonvpn.android.redesign.settings.ui.customdns.CustomDnsScreen
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentPrimaryLabel
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.vpn.ProtocolSelection
import me.proton.core.accountmanager.presentation.compose.viewmodel.AccountSettingsViewState
import me.proton.core.domain.entity.UserId

@Composable
@Preview(heightDp = 1625)
fun SettingsCredentialless() {
    val settings = SettingsData(true)

    ProtonVpnPreview(addSurface = false) {
        SettingsView(
            accountSettingsViewState = settings.credentiallessAccountViewState,
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
            onWidgetClick = {},
            onDebugToolsClick = {},
        )
    }
}

@Composable
@Preview()
fun AdvancedSettingsNotConnectedPaid() {
    val settingsPaid = SettingsData(false, false)
    ProtonVpnPreview(addSurface = true) {
        AdvancedSettings(
            onClose = {},
            profileOverrideInfo = settingsPaid.overrideInfo,
            altRouting = settingsPaid.settingsViewState.altRouting,
            allowLan = settingsPaid.settingsViewState.lanConnections,
            natType = settingsPaid.settingsViewState.natType,
            onAltRoutingChange = {},
            onAllowLanChange = {},
            onNatTypeRestricted = {},
            onNatTypeLearnMore = {},
            onAllowLanRestricted = {},
            onNavigateToNatType = {},
            ipV6 = settingsPaid.settingsViewState.ipV6,
            onIPv6Toggle = {},
            onIPv6InfoClick = {},
            customDns = settingsPaid.settingsViewState.customDns,
            onCustomDnsLearnMore = {},
            onCustomDnsRestricted = {},
            onNavigateToCustomDns = {},
        )
    }
}

@Composable
@Preview()
fun CustomDnsEmptyState() {
    ProtonVpnPreview(addSurface = true) {
        CustomDnsScreen(
            onClose = {},
            onDnsChange = {},
            onDnsToggled = {},
            onAddNewAddress = {},
            onLearnMore = {},
            showReconnectionDialog = {},
            viewState = SettingsViewModel.CustomDnsViewState(
                dnsViewState =
                SettingViewState.CustomDns(
                    enabled = false,
                    customDns = emptyList(),
                    overrideProfilePrimaryLabel = null,
                    isFreeUser = false
                ),
                isConnected = false
            )
        )
    }
}

@Composable
@Preview()
fun CustomDnsState() {
    val settingsPaid = SettingsData(false, true)
    ProtonVpnPreview(addSurface = true) {
        CustomDnsScreen(
            onClose = {},
            onDnsChange = {},
            onDnsToggled = {},
            onAddNewAddress = {},
            onLearnMore = {},
            showReconnectionDialog = {},
            viewState = SettingsViewModel.CustomDnsViewState(
                dnsViewState = settingsPaid.settingsViewState.customDns!!,
                isConnected = false
            )

        )
    }
}

@Preview
@Composable
fun AdvancedSettingsProfileConnected() {
    val settingsPaid = SettingsData(false, true)
    ProtonVpnPreview(addSurface = true) {
        AdvancedSettings(
            onClose = {},
            profileOverrideInfo = settingsPaid.overrideInfo,
            altRouting = settingsPaid.settingsViewState.altRouting,
            allowLan = settingsPaid.settingsViewState.lanConnections,
            natType = settingsPaid.settingsViewState.natType,
            onAltRoutingChange = {},
            onAllowLanChange = {},
            onNatTypeRestricted = {},
            onNatTypeLearnMore = {},
            onAllowLanRestricted = {},
            onNavigateToNatType = {},
            ipV6 = settingsPaid.settingsViewState.ipV6,
            onIPv6Toggle = {},
            onIPv6InfoClick = {},
            customDns = settingsPaid.settingsViewState.customDns,
            onCustomDnsLearnMore = {},
            onCustomDnsRestricted = {},
            onNavigateToCustomDns = {},
        )
    }
}

@Preview
@Composable
fun AdvancedSettingsFree() {
    val settingsPaid = SettingsData(true, false)
    ProtonVpnPreview(addSurface = true) {
        AdvancedSettings(
            onClose = {},
            profileOverrideInfo = settingsPaid.overrideInfo,
            altRouting = settingsPaid.settingsViewState.altRouting,
            allowLan = settingsPaid.settingsViewState.lanConnections,
            natType = settingsPaid.settingsViewState.natType,
            onAltRoutingChange = {},
            onAllowLanChange = {},
            onNatTypeRestricted = {},
            onNatTypeLearnMore = {},
            onAllowLanRestricted = {},
            onNavigateToNatType = {},
            customDns = settingsPaid.settingsViewState.customDns,
            onCustomDnsLearnMore = {},
            onCustomDnsRestricted = {},
            onNavigateToCustomDns = {},
            ipV6 = settingsPaid.settingsViewState.ipV6,
            onIPv6Toggle = {},
            onIPv6InfoClick = {},
        )
    }
}

@Preview
@Composable
fun AddNewDnsScreenPreview() {
    ProtonVpnPreview(addSurface = true) {
        AddNewDnsScreen(
            addDnsState = AddDnsResult.WaitingForInput,
            onClose = {},
            onAddDns = {}
        )
    }
}

@Preview
@Composable
fun AddNewDnsScreenErrorPreview() {
    ProtonVpnPreview(addSurface = true) {
        AddNewDnsScreen(
            addDnsState = AddDnsError.InvalidInput,
            onClose = {},
            onAddDns = {}
        )
    }
}

@Composable
@Preview(heightDp = 1625)
fun SettingsPaidProfileConnected() {
    val settingsPaid = SettingsData(false, true)
    ProtonVpnPreview(addSurface = false) {
        SettingsView(
            accountSettingsViewState = settingsPaid.credentiallessAccountViewState,
            viewState = settingsPaid.settingsViewState,
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
            onWidgetClick = {},
            onDebugToolsClick = {}
        )
    }
}

private class SettingsData(isFree: Boolean, connectedToProfile: Boolean = false) {
    private val splitTunneling = SettingsViewModel.SettingViewState.SplitTunneling(
        false, SplitTunnelingMode.EXCLUDE_ONLY, listOf(), listOf(), isFree
    )
    val overrideInfo = if (connectedToProfile) SettingsViewModel.ProfileOverrideInfo(ConnectIntentPrimaryLabel.Profile(
        "name",
        CountryId.sweden,
        false,
        ProfileIcon.Icon1,
        ProfileColor.Color1
    ), "name") else null

    private val netshield = SettingsViewModel.SettingViewState.NetShield(true, isFree, overrideInfo?.primaryLabel)
    private val vpnAccelerator = SettingsViewModel.SettingViewState.VpnAccelerator(true, isFree)
    private val protocol = SettingsViewModel.SettingViewState.Protocol(ProtocolSelection.SMART, overrideInfo?.primaryLabel)
    private val altRouting = SettingsViewModel.SettingViewState.AltRouting(true)
    private val customDns = SettingsViewModel.SettingViewState.CustomDns(
        true,
        listOf("1.1.1.1", "8.8.8.8", "2001:db8:3333:4444:5555:6666:7777:8888"),
        overrideInfo?.primaryLabel,
        isFree
    )
    private val lanConnections =
        SettingsViewModel.SettingViewState.LanConnections(true, isFree, overrideInfo?.primaryLabel)
    private val natType =
        SettingsViewModel.SettingViewState.Nat(NatType.Strict, isFree, overrideInfo?.primaryLabel)
    private val ipV6 = SettingsViewModel.SettingViewState.IPv6(enabled = true)

    val settingsViewState = SettingsViewModel.SettingsViewState(
        profileOverrideInfo = overrideInfo,
        netShield = netshield,
        splitTunneling = splitTunneling,
        vpnAccelerator = vpnAccelerator,
        showSignOut = false,
        accountScreenEnabled = false,
        protocol = protocol,
        altRouting = altRouting,
        buildInfo = null,
        lanConnections = lanConnections,
        natType = natType,
        versionName = "1.2.3.4",
        isWidgetDiscovered = false,
        ipV6 = ipV6,
        customDns = customDns,
        showDebugTools = false
    )

    val credentiallessAccountViewState =
        if (isFree)
            AccountSettingsViewState.CredentialLess(UserId("test"))
        else
            AccountSettingsViewState.Hidden
}
