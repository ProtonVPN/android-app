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

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.R
import com.protonvpn.android.profiles.ui.nav.ProfileCreationTarget
import com.protonvpn.android.redesign.app.ui.SettingsChangeViewModel
import com.protonvpn.android.redesign.base.ui.LocalVpnUiDelegate
import com.protonvpn.android.redesign.base.ui.largeScreenContentPadding
import com.protonvpn.android.redesign.settings.ui.nav.SubSettingsScreen
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.telemetry.UpgradeSource
import com.protonvpn.android.ui.planupgrade.CarouselUpgradeDialogActivity
import com.protonvpn.android.ui.planupgrade.UpgradeAllowLanHighlightsFragment
import com.protonvpn.android.ui.planupgrade.UpgradeModerateNatHighlightsFragment
import com.protonvpn.android.ui.settings.SettingsSplitTunnelAppsActivity
import com.protonvpn.android.ui.settings.SettingsSplitTunnelIpsActivity
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.DebugUtils
import com.protonvpn.android.utils.openUrl
import com.protonvpn.android.utils.openVpnSettings
import com.protonvpn.android.widget.ui.WidgetAddScreen
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultStrongNorm
import me.proton.core.domain.entity.UserId
import me.proton.core.usersettings.presentation.entity.SettingsInput
import me.proton.core.usersettings.presentation.ui.StartPasswordManagement
import me.proton.core.usersettings.presentation.ui.StartSecurityKeys
import me.proton.core.usersettings.presentation.ui.StartUpdateRecoveryEmail
import me.proton.core.presentation.R as CoreR

@Composable
fun SubSettingsRoute(
    viewModel: SettingsViewModel,
    settingsChangeViewModel: SettingsChangeViewModel,
    type: SubSettingsScreen.Type,
    onClose: () -> Unit,
    onNavigateToSubSetting: (SubSettingsScreen.Type) -> Unit,
    onNavigateToEditProfile: (Long, ProfileCreationTarget) -> Unit,
) {
    val context = LocalContext.current
    val vpnUiDelegate = LocalVpnUiDelegate.current

    val onSplitTunnelUpdated = { savedChange: Boolean? ->
        if (savedChange == true)
            settingsChangeViewModel.onSplitTunnelingUpdated(vpnUiDelegate)
    }
    val splitTunnelIpLauncher = rememberLauncherForActivityResult(
        SettingsSplitTunnelIpsActivity.createContract(), onSplitTunnelUpdated)
    val splitTunnelAppsLauncher = rememberLauncherForActivityResult(
        SettingsSplitTunnelAppsActivity.createContract(), onSplitTunnelUpdated)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = ProtonTheme.colors.backgroundNorm)
            .navigationBarsPadding()
    ) {
        when (type) {
            SubSettingsScreen.Type.VpnAccelerator -> {
                val vpnAccelerator =
                    viewModel.vpnAccelerator.collectAsStateWithLifecycle(initialValue = null).value
                DebugUtils.debugAssert { vpnAccelerator?.isRestricted != true }
                if (vpnAccelerator != null) {
                    VpnAccelerator(
                        onClose,
                        vpnAccelerator,
                        { context.openUrl(Constants.VPN_ACCELERATOR_INFO_URL) },
                        settingsChangeViewModel::toggleVpnAccelerator,
                    )
                }
            }

            SubSettingsScreen.Type.NetShield -> {
                val netShield = viewModel.netShield.collectAsStateWithLifecycle(initialValue = null).value
                if (netShield != null) {
                    NetShieldSetting(
                        onClose = onClose,
                        netShield = netShield,
                        onLearnMore = { context.openUrl(Constants.URL_NETSHIELD_LEARN_MORE) },
                        onNetShieldToggle = settingsChangeViewModel::toggleNetShield
                    )
                }
            }

            SubSettingsScreen.Type.Account -> {
                val accountViewState = viewModel.accountSettings.collectAsStateWithLifecycle(initialValue = null).value
                val changePasswordContract = rememberLauncherForActivityResult(StartPasswordManagement()) {}
                val changeRecoveryEmailContract = rememberLauncherForActivityResult(StartUpdateRecoveryEmail()) {}
                val securityKeysContract = rememberLauncherForActivityResult(StartSecurityKeys()) {}
                if (accountViewState != null) {
                    AccountSettings(
                        viewState = accountViewState,
                        onClose = onClose,
                        onChangePassword = { changePasswordContract.launch(accountViewState.userId.toInput()) },
                        onChangeRecoveryEmail = { changeRecoveryEmailContract.launch(accountViewState.userId.toInput()) },
                        onSecurityKeysClicked = { securityKeysContract.launch(accountViewState.userId.toInput()) },
                        onOpenMyAccount = { context.openUrl(Constants.URL_ACCOUNT_LOGIN) },
                        onDeleteAccount = { context.openUrl(Constants.URL_ACCOUNT_DELETE) },
                        onUpgrade = {
                            CarouselUpgradeDialogActivity.launch(
                                context,
                                UpgradeSource.ACCOUNT,
                                focusedFragmentClass = null
                            )
                        }
                    )
                }
            }

            SubSettingsScreen.Type.Advanced -> {
                val advancedViewState = viewModel.advancedSettings.collectAsStateWithLifecycle(initialValue = null).value
                if (advancedViewState != null) {
                    SettingOverrideDialogHandler(onNavigateToEditProfile) { onOverrideSettingClick ->
                        AdvancedSettings(
                            onClose = onClose,
                            profileOverrideInfo = advancedViewState.profileOverrideInfo,
                            altRouting = advancedViewState.altRouting,
                            allowLan = advancedViewState.lanConnections,
                            natType = advancedViewState.natType,
                            onAltRoutingChange = settingsChangeViewModel::toggleAltRouting,
                            onAllowLanChange = {
                                onOverrideSettingClick(OverrideType.LAN) {
                                    settingsChangeViewModel.toggleLanConnections(
                                        vpnUiDelegate
                                    )
                                }
                            },
                            onNatTypeLearnMore = { context.openUrl(Constants.MODERATE_NAT_INFO_URL) },
                            onNavigateToNatType = {
                                onOverrideSettingClick(OverrideType.NatType) {
                                    onNavigateToSubSetting(SubSettingsScreen.Type.NatType)
                                }
                            },
                            onAllowLanRestricted = {
                                onOverrideSettingClick(OverrideType.LAN) {
                                    CarouselUpgradeDialogActivity.launch<UpgradeAllowLanHighlightsFragment>(
                                        context
                                    )
                                }
                            },
                            onNatTypeRestricted = {
                                CarouselUpgradeDialogActivity.launch<UpgradeModerateNatHighlightsFragment>(
                                    context
                                )
                            },
                        )
                    }
                }
            }

            SubSettingsScreen.Type.DebugTools -> {
                val debugToolsViewModel = hiltViewModel<DebugToolsViewModel>()
                val state = debugToolsViewModel.state.collectAsStateWithLifecycle(initialValue = null).value
                DebugTools(
                    onClose = onClose,
                    onConnectGuestHole = debugToolsViewModel::connectGuestHole,
                    onRefreshConfig = debugToolsViewModel::refreshConfig,
                    netzone = state?.netzone ?: "",
                    country = state?.country ?: "",
                    setNetzone = debugToolsViewModel::setNetzone,
                    setCountry = debugToolsViewModel::setCountry,
                )
            }

            SubSettingsScreen.Type.NatType -> {
                val nat = viewModel.natType.collectAsStateWithLifecycle(initialValue = null).value
                if (nat != null) {
                    NatTypeSettings(
                        onClose = onClose,
                        nat = nat,
                        onNatTypeChange = { newValue ->
                            settingsChangeViewModel.setNatType(newValue)
                            onClose()
                        },
                    )
                }
            }

            SubSettingsScreen.Type.KillSwitch -> {
                KillSwitchInfo(
                    onOpenVpnSettings = { context.openVpnSettings() },
                    onLearnMore = { context.openUrl(Constants.KILL_SWITCH_INFO_URL) },
                    onClose = onClose,
                )
            }

            SubSettingsScreen.Type.Protocol -> {
                val protocolSettings = viewModel.protocol.collectAsStateWithLifecycle(initialValue = null).value
                if (protocolSettings != null) {
                    ProtocolSettings(
                        onClose = onClose,
                        protocolViewState = protocolSettings,
                        onLearnMore = { context.openUrl(Constants.PROTOCOL_INFO_URL) },
                        onProtocolSelected = { newProtocol ->
                            settingsChangeViewModel.updateProtocol(vpnUiDelegate, newProtocol)
                            onClose()
                        }
                    )
                }
            }

            SubSettingsScreen.Type.SplitTunneling -> {
                val splitTunnelingSettings = viewModel.splitTunneling.collectAsStateWithLifecycle(initialValue = null).value
                if (splitTunnelingSettings != null) {
                    val onModeSet = { mode: SplitTunnelingMode ->
                        settingsChangeViewModel.setSplitTunnelingMode(vpnUiDelegate, mode)
                    }
                    SplitTunnelingSubSetting(
                        onClose = onClose,
                        splitTunneling = splitTunnelingSettings,
                        onLearnMore = { context.openUrl(Constants.SPLIT_TUNNELING_INFO_URL) },
                        onSplitTunnelToggle = { settingsChangeViewModel.toggleSplitTunneling(vpnUiDelegate) },
                        onSplitTunnelModeSelected = onModeSet,
                        onAppsClick = { mode -> splitTunnelAppsLauncher.launch(mode) },
                        onIpsClick = { mode -> splitTunnelIpLauncher.launch(mode) }
                    )
                }
            }

            SubSettingsScreen.Type.DefaultConnection -> {
                DefaultConnectionSetting(onClose)
            }

            SubSettingsScreen.Type.IconChange -> {
                IconSelectionSetting(
                    activeIcon = viewModel.getCurrentAppIcon(),
                    onIconChange = { viewModel.setNewAppIcon(it) },
                    onClose = onClose
                )
            }

            SubSettingsScreen.Type.Widget -> {
                WidgetAddScreen(onClose = onClose)
            }
        }
    }
}

private fun UserId.toInput() = SettingsInput(this.id)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BasicSubSetting(
    modifier: Modifier = Modifier,
    title: String,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopAppBar(
            title = {
                Text(text = title, style = ProtonTheme.typography.defaultStrongNorm)
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = ProtonTheme.colors.backgroundNorm
            ),
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(
                        painter = painterResource(id = CoreR.drawable.ic_arrow_back),
                        contentDescription = stringResource(id = R.string.accessibility_back)
                    )
                }
            },
        )
        content()
    }
}

@Composable
fun SubSetting(
    modifier: Modifier = Modifier,
    title: String,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    BasicSubSetting(
        modifier = modifier,
        title = title,
        onClose = onClose,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .largeScreenContentPadding(),
        ) {
            content()
        }
    }
}

@Composable
fun SubSettingWithLazyContent(
    modifier: Modifier = Modifier,
    title: String,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    BasicSubSetting(
        modifier = modifier,
        title = title,
        onClose = onClose
    ) {
        Box(
            modifier = Modifier
                .largeScreenContentPadding()
        ) {
            content()
        }
    }
}
