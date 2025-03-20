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

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.AnnotatedClickableText
import com.protonvpn.android.base.ui.ProtonTextButton
import com.protonvpn.android.base.ui.SimpleTopAppBar
import com.protonvpn.android.base.ui.TopAppBarBackIcon
import com.protonvpn.android.profiles.ui.nav.ProfileCreationTarget
import com.protonvpn.android.redesign.app.ui.SettingsChangeViewModel
import com.protonvpn.android.redesign.base.ui.DIALOG_CONTENT_PADDING
import com.protonvpn.android.redesign.base.ui.InfoSheet
import com.protonvpn.android.redesign.base.ui.InfoType
import com.protonvpn.android.redesign.base.ui.LocalVpnUiDelegate
import com.protonvpn.android.redesign.base.ui.ProtonBasicAlert
import com.protonvpn.android.redesign.base.ui.ProtonSnackbarType
import com.protonvpn.android.redesign.base.ui.collectAsEffect
import com.protonvpn.android.redesign.base.ui.largeScreenContentPadding
import com.protonvpn.android.redesign.base.ui.rememberInfoSheetState
import com.protonvpn.android.redesign.base.ui.showSnackbar
import com.protonvpn.android.redesign.settings.ui.customdns.AddDnsResult
import com.protonvpn.android.redesign.settings.ui.customdns.AddNewDnsScreen
import com.protonvpn.android.redesign.settings.ui.customdns.CustomDnsScreen
import com.protonvpn.android.redesign.settings.ui.nav.SubSettingsScreen
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.telemetry.UpgradeSource
import com.protonvpn.android.ui.planupgrade.CarouselUpgradeDialogActivity
import com.protonvpn.android.ui.planupgrade.UpgradeAdvancedCustomizationHighlightsFragment
import com.protonvpn.android.ui.settings.SettingsSplitTunnelAppsActivity
import com.protonvpn.android.ui.settings.SettingsSplitTunnelIpsActivity
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.DebugUtils
import com.protonvpn.android.utils.openUrl
import com.protonvpn.android.utils.openVpnSettings
import com.protonvpn.android.widget.ui.WidgetAddScreen
import kotlinx.coroutines.flow.receiveAsFlow
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.domain.entity.UserId
import me.proton.core.usersettings.presentation.entity.SettingsInput
import me.proton.core.usersettings.presentation.ui.StartPasswordManagement
import me.proton.core.usersettings.presentation.ui.StartSecurityKeys
import me.proton.core.usersettings.presentation.ui.StartUpdateRecoveryEmail

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

    val infoSheetState = rememberInfoSheetState()
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
                    FeatureSubSetting(
                        imageRes = R.drawable.setting_vpn_accelerator,
                        setting = vpnAccelerator,
                        onClose = onClose,
                        onLearnMore = { context.openUrl(Constants.VPN_ACCELERATOR_INFO_URL) },
                        onToggle = settingsChangeViewModel::toggleVpnAccelerator,
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
                        onDisableCustomDns = { settingsChangeViewModel.disableCustomDns(vpnUiDelegate) },
                        onCustomDnsLearnMore = { context.openUrl(Constants.URL_NETSHIELD_CUSTOM_DNS_LEARN_MORE) },
                        onOpenPrivateDnsSettings = { context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) },
                        onPrivateDnsLearnMore = { context.openUrl(Constants.URL_NETSHIELD_PRIVATE_DNS_LEARN_MORE) },
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
                            customDns = advancedViewState.customDns,
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
                            onNavigateToCustomDns = {
                                onNavigateToSubSetting(SubSettingsScreen.Type.CustomDns)
                            },
                            onAllowLanRestricted = {
                                onOverrideSettingClick(OverrideType.LAN) {
                                    CarouselUpgradeDialogActivity.launch<UpgradeAdvancedCustomizationHighlightsFragment>(
                                        context
                                    )
                                }
                            },
                            onNatTypeRestricted = {
                                CarouselUpgradeDialogActivity.launch<UpgradeAdvancedCustomizationHighlightsFragment>(
                                    context
                                )
                            },
                            ipV6 = advancedViewState.ipV6,
                            onIPv6Toggle = { settingsChangeViewModel.toggleIPv6(vpnUiDelegate) },
                            onIPv6InfoClick = { infoSheetState.show(InfoType.IPv6Traffic) },
                            onCustomDnsLearnMore = {
                                context.openUrl(Constants.URL_CUSTOM_DNS_LEARN_MORE)
                            },
                            onCustomDnsRestricted = {
                                CarouselUpgradeDialogActivity.launch<UpgradeAdvancedCustomizationHighlightsFragment>(
                                    context
                                )
                            }
                        )
                    }
                }
            }
            SubSettingsScreen.Type.AddNewDns -> {
                val viewState = settingsChangeViewModel.addDnsResultFlow.collectAsStateWithLifecycle().value
                AddNewDnsScreen(
                    onClose = {
                        settingsChangeViewModel.addDnsResultFlow.value = AddDnsResult.WaitingForInput
                        onClose()
                    },
                    addDnsState = viewState,
                    onAddDns = settingsChangeViewModel::addNewDns,
                    onTextChanged = settingsChangeViewModel::onAddNewDnsTextChanged
                )
            }
            SubSettingsScreen.Type.CustomDns -> {
                val viewState = viewModel.customDnsViewState.collectAsStateWithLifecycle(null).value
                if (viewState != null) {
                    val snackbarHostState = remember { SnackbarHostState() }
                    CustomDnsScreen(
                        onClose = onClose,
                        onDnsToggled = {
                            settingsChangeViewModel.toggleCustomDns()
                        },
                        onDnsChange = {
                            settingsChangeViewModel.updateCustomDnsList(it)
                        },
                        onAddNewAddress = {
                            onNavigateToSubSetting(SubSettingsScreen.Type.AddNewDns)
                        },
                        onItemRemoved = {
                            settingsChangeViewModel.removeDnsItem(it)
                        },
                        onLearnMore = {
                            context.openUrl(Constants.URL_CUSTOM_DNS_LEARN_MORE)
                        },
                        onPrivateDnsLearnMore = {
                            context.openUrl(Constants.URL_CUSTOM_DNS_PRIVATE_DNS_LEARN_MORE)
                        },
                        onOpenPrivateDnsSettings = { context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) },
                        showReconnectionDialog = {
                            settingsChangeViewModel.showDnsReconnectionDialog(vpnUiDelegate)
                        },
                        snackbarHostState = snackbarHostState,
                        viewState = viewState,
                    )
                    val itemRemovedMessage = stringResource(R.string.custom_dns_item_removed_snackbar)
                    val snackUndo = stringResource(R.string.undo)
                    LaunchedEffect(Unit) {
                        settingsChangeViewModel.undoSnackbarFlow.collect { event ->
                            val result = snackbarHostState.showSnackbar(
                                message = itemRemovedMessage,
                                actionLabel = snackUndo,
                                duration = SnackbarDuration.Short,
                                type = ProtonSnackbarType.NORM
                            )

                            if (result == SnackbarResult.ActionPerformed) {
                                settingsChangeViewModel.undoRemoval(event)
                            }
                        }
                    }
                }
                var customDnsNetShieldConflictDialog by remember { mutableStateOf(false) }
                settingsChangeViewModel.eventShowCustomDnsNetShieldConflict.receiveAsFlow()
                    .collectAsEffect { customDnsNetShieldConflictDialog = true }
                if (customDnsNetShieldConflictDialog) {
                    CustomDnsNetShieldConflictDialog(
                        onDismissRequest = { customDnsNetShieldConflictDialog = false },
                        onLearnMore = { context.openUrl(Constants.URL_NETSHIELD_CUSTOM_DNS_LEARN_MORE) },
                    )
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
    InfoSheet(infoSheetState, onOpenUrl = { context.openUrl(it) })
}

private fun UserId.toInput() = SettingsInput(this.id)

@Composable
private fun CustomDnsNetShieldConflictDialog(
    onLearnMore: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    ProtonBasicAlert(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier.padding(horizontal = DIALOG_CONTENT_PADDING)
        ) {
            Text(
                stringResource(R.string.settings_custom_dns_netshield_conflict_dialog_title),
                style = ProtonTheme.typography.subheadline,
            )
            Spacer(Modifier.height(16.dp))
            AnnotatedClickableText(
                fullText = stringResource(R.string.settings_custom_dns_netshield_conflict_dialog_message),
                annotatedPart = stringResource(R.string.learn_more),
                color = ProtonTheme.colors.textWeak,
                onAnnotatedClick = onLearnMore,
            )
            Spacer(Modifier.height(24.dp))
            ProtonTextButton(onClick = onDismissRequest, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.got_it))
            }
        }
    }
}

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
        SimpleTopAppBar(
            title = { Text(text = title) },
            navigationIcon = { TopAppBarBackIcon(onClose) },
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
