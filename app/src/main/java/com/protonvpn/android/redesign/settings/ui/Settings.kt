/*
 * Copyright (c) 2023 Proton AG
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

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
import android.provider.Settings.EXTRA_APP_PACKAGE
import android.provider.Settings.EXTRA_CHANNEL_ID
import androidx.activity.compose.LocalActivity
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.LabelBadge
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.update.VpnUpdateBanner
import com.protonvpn.android.profiles.data.ProfileColor
import com.protonvpn.android.profiles.data.ProfileIcon
import com.protonvpn.android.profiles.ui.nav.ProfileCreationStepTarget
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.base.ui.CollapsibleToolbarScaffold
import com.protonvpn.android.redesign.base.ui.collectAsEffect
import com.protonvpn.android.redesign.base.ui.largeScreenContentPadding
import com.protonvpn.android.redesign.reports.ui.BugReportActivity
import com.protonvpn.android.redesign.settings.ui.nav.SubSettingsScreen
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentPrimaryLabel
import com.protonvpn.android.redesign.vpn.ui.label
import com.protonvpn.android.ui.drawer.LogActivity
import com.protonvpn.android.ui.drawer.bugreport.DynamicReportActivity
import com.protonvpn.android.ui.planupgrade.CarouselUpgradeDialogActivity
import com.protonvpn.android.ui.planupgrade.UpgradeNetShieldHighlightsFragment
import com.protonvpn.android.ui.planupgrade.UpgradeSplitTunnelingHighlightsFragment
import com.protonvpn.android.ui.planupgrade.UpgradeVpnAcceleratorHighlightsFragment
import com.protonvpn.android.ui.settings.OssLicensesActivity
import com.protonvpn.android.ui.settings.SettingsTelemetryActivity
import com.protonvpn.android.update.AppUpdateInfo
import com.protonvpn.android.utils.openUrl
import me.proton.core.accountmanager.presentation.compose.AccountSettingsInfo
import me.proton.core.accountmanager.presentation.compose.viewmodel.AccountSettingsViewModel
import me.proton.core.accountmanager.presentation.compose.viewmodel.AccountSettingsViewState
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.captionNorm
import me.proton.core.compose.theme.captionWeak
import me.proton.core.compose.theme.defaultNorm
import me.proton.core.compose.theme.defaultSmallWeak
import me.proton.core.compose.theme.defaultWeak
import me.proton.core.devicemigration.presentation.settings.SignInToAnotherDeviceItem
import me.proton.core.domain.entity.UserId
import me.proton.core.presentation.utils.openMarketLink
import me.proton.core.telemetry.presentation.ProductMetricsDelegateOwner
import me.proton.core.telemetry.presentation.compose.LocalProductMetricsDelegateOwner
import me.proton.core.presentation.R as CoreR


@SuppressLint("InlinedApi")
@Composable
fun SettingsRoute(
    onSignUpClick: () -> Unit,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onNavigateToSubSetting: (SubSettingsScreen.Type) -> Unit,
    onNavigateToEditProfile: (Long, ProfileCreationStepTarget) -> Unit
) {
    val activity: Activity = requireNotNull(LocalActivity.current)
    val viewModel = hiltViewModel<SettingsViewModel>()
    val viewState = viewModel.viewState.collectAsStateWithLifecycle(initialValue = null).value

    val accountSettingsViewModel = hiltViewModel<AccountSettingsViewModel>()
    val accountSettingsViewState = accountSettingsViewModel.state.collectAsStateWithLifecycle().value

    if (viewState == null || accountSettingsViewState == AccountSettingsViewState.Hidden) {
        // Return a composable even when viewState == null to avoid transition glitches
        Box(modifier = Modifier.fillMaxSize()) {}
        return
    }

    viewModel.event.collectAsEffect { event ->
        when(event) {
            SettingsViewModel.UiEvent.NavigateToWidgetInstructions ->
                onNavigateToSubSetting(SubSettingsScreen.Type.Widget)
        }
    }

    val context = LocalContext.current

    CompositionLocalProvider(
        LocalProductMetricsDelegateOwner provides ProductMetricsDelegateOwner(accountSettingsViewModel)
    ) {
        SettingOverrideDialogHandler(onNavigateToEditProfile) { onOverrideSettingClick ->
            SettingsView(
                viewState = viewState,
                accountSettingsViewState = accountSettingsViewState,
                onSignUpClick = onSignUpClick,
                onSignInClick = onSignInClick,
                onSignOutClick = onSignOutClick,
                onAppUpdateClick = { updateInfo ->
                    viewModel.onAppUpdateClick(activity, updateInfo)
                },
                onAccountClick = {
                    if (viewState.accountScreenEnabled)
                        onNavigateToSubSetting(SubSettingsScreen.Type.Account)
                },
                onNetShieldClick = {
                    val overrideType = if (viewState.customDns?.isPrivateDnsActive == true)
                        OverrideType.NetShieldPrivateDnsConflict else OverrideType.NetShield
                    onOverrideSettingClick(overrideType) {
                        onNavigateToSubSetting(SubSettingsScreen.Type.NetShield)
                    }
                },
                onNetShieldUpgradeClick = {
                    CarouselUpgradeDialogActivity.launch<UpgradeNetShieldHighlightsFragment>(context)
                },
                onSplitTunnelClick = {
                    onNavigateToSubSetting(SubSettingsScreen.Type.SplitTunneling)
                },
                onSplitTunnelUpgrade = {
                    CarouselUpgradeDialogActivity.launch<UpgradeSplitTunnelingHighlightsFragment>(context)
                },
                onAlwaysOnClick = {
                    onNavigateToSubSetting(SubSettingsScreen.Type.KillSwitch)
                },
                onProtocolClick = {
                    onOverrideSettingClick(OverrideType.Protocol) {
                        onNavigateToSubSetting(SubSettingsScreen.Type.Protocol)
                    }
                },
                onDefaultConnectionClick = {
                    onNavigateToSubSetting(SubSettingsScreen.Type.DefaultConnection)
                },
                onVpnAcceleratorClick = {
                    onNavigateToSubSetting(SubSettingsScreen.Type.VpnAccelerator)
                },
                onVpnAcceleratorUpgrade = {
                    CarouselUpgradeDialogActivity.launch<UpgradeVpnAcceleratorHighlightsFragment>(context)
                },
                onAdvancedSettingsClick = {
                    onNavigateToSubSetting(SubSettingsScreen.Type.Advanced)
                },
                onNotificationsClick = {
                    val intent = Intent(ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(EXTRA_APP_PACKAGE, context.packageName)
                        putExtra(EXTRA_CHANNEL_ID, context.applicationInfo.uid)
                    }

                    context.startActivity(intent)
                },
                onOnHelpCenterClick = {
                    context.openUrl(context.getString(R.string.contact_support_link))
                },
                onReportBugClick = {
                    if (viewState.isRedesignedBugReportFeatureFlagEnabled) {
                        context.startActivity(Intent(context, BugReportActivity::class.java))
                    } else {
                        context.startActivity(Intent(context, DynamicReportActivity::class.java))
                    }
                },
                onDebugLogsClick = {
                    context.startActivity(Intent(context, LogActivity::class.java))
                },
                onHelpFightClick = {
                    context.startActivity(Intent(context, SettingsTelemetryActivity::class.java))
                },
                onThemeClick = {
                    onNavigateToSubSetting(SubSettingsScreen.Type.Theme)
                },
                onIconChangeClick = {
                    onNavigateToSubSetting(SubSettingsScreen.Type.IconChange)
                },
                onRateUsClick = {
                    context.openMarketLink()
                },
                onThirdPartyLicensesClick = {
                    context.startActivity(Intent(context, OssLicensesActivity::class.java))
                },
                onWidgetClick = {
                    viewModel.onWidgetSettingClick()
                },
                onDebugToolsClick = {
                    onNavigateToSubSetting(SubSettingsScreen.Type.DebugTools)
                }
            )
        }
    }
}

@Composable
fun SettingsView(
    modifier: Modifier = Modifier,
    viewState: SettingsViewModel.SettingsViewState,
    accountSettingsViewState: AccountSettingsViewState,
    onAppUpdateClick: (AppUpdateInfo) -> Unit,
    onAccountClick: () -> Unit,
    onSignUpClick: () -> Unit,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onNetShieldClick: () -> Unit,
    onNetShieldUpgradeClick: () -> Unit,
    onSplitTunnelClick: () -> Unit,
    onSplitTunnelUpgrade: () -> Unit,
    onAlwaysOnClick: () -> Unit,
    onDefaultConnectionClick: () -> Unit,
    onProtocolClick: () -> Unit,
    onVpnAcceleratorClick: () -> Unit,
    onVpnAcceleratorUpgrade: () -> Unit,
    onAdvancedSettingsClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onIconChangeClick: () -> Unit,
    onThemeClick: () -> Unit,
    onWidgetClick: () -> Unit,
    onOnHelpCenterClick: () -> Unit,
    onReportBugClick: () -> Unit,
    onDebugLogsClick: () -> Unit,
    onHelpFightClick: () -> Unit,
    onRateUsClick: () -> Unit,
    onThirdPartyLicensesClick: () -> Unit,
    onDebugToolsClick: () -> Unit,
) {
    CollapsibleToolbarScaffold(
        titleResId = R.string.settings_title,
        contentWindowInsets = WindowInsets.statusBars,
        modifier = modifier,
    ) { innerPadding ->
        val extraScreenPadding = largeScreenContentPadding()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = extraScreenPadding)
        ) {
            val horizontalItemPadding = Modifier.padding(horizontal = 16.dp)
            VpnUpdateBanner(
                message = stringResource(R.string.update_banner_description_settings),
                viewState = viewState.appUpdateBannerState,
                onAppUpdate = onAppUpdateClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(horizontalItemPadding),
            )

            viewState.profileOverrideInfo?.let {
                ProfileOverrideView(
                    modifier = Modifier.padding(16.dp),
                    profileOverrideInfo = it
                )
            }
            AccountCategory(
                state = accountSettingsViewState,
                onAccountClick = onAccountClick,
                onSignUpClick = onSignUpClick,
                onSignInClick = onSignInClick,
                onSignOutClick = onSignOutClick,
                showSingInOnAnotherDeviceQr = viewState.showSingInOnAnotherDeviceQr
            )
            FeatureCategory(
                viewState = viewState,
                onNetShieldClick = onNetShieldClick,
                onNetShieldUpgrade = onNetShieldUpgradeClick,
                onSplitTunnelClick = onSplitTunnelClick,
                onSplitTunnelUpgrade = onSplitTunnelUpgrade,
                onAlwaysOnClick = onAlwaysOnClick
            )
            Category(
                modifier = horizontalItemPadding,
                stringResource(id = R.string.settings_connection_category)
            ) {
                viewState.defaultConnection?.let { connnection ->
                    val connectionLabel = with(connnection) {
                        predefinedTitle?.let { stringResource(id = it) } ?: recentLabel?.label()
                    }
                    SettingRowWithIcon(
                        icon = connnection.iconRes,
                        title = stringResource(id = connnection.titleRes),
                        settingValue = connectionLabel?.let { SettingValue.SettingText(it) },
                        onClick = onDefaultConnectionClick,
                    )
                }
                SettingRowWithIcon(
                    icon = viewState.protocol.iconRes,
                    title = stringResource(id = viewState.protocol.titleRes),
                    onClick = onProtocolClick,
                    settingValue = viewState.protocol.settingValueView
                )
                SettingRowWithIcon(
                    icon = viewState.vpnAccelerator.iconRes,
                    title = stringResource(id = viewState.vpnAccelerator.titleRes),
                    onClick = if (viewState.vpnAccelerator.isRestricted)
                        onVpnAcceleratorUpgrade else onVpnAcceleratorClick,
                    trailingIcon = R.drawable.vpn_plus_badge.takeIf { viewState.vpnAccelerator.isRestricted },
                    trailingIconTint = false,
                    settingValue = viewState.vpnAccelerator.settingValueView
                )
                SettingRowWithIcon(
                    icon = CoreR.drawable.ic_proton_sliders,
                    title = stringResource(id = R.string.settings_advanced_settings_title),
                    onClick = onAdvancedSettingsClick,
                )
            }

            Category(
                modifier = horizontalItemPadding,
                stringResource(id = R.string.settings_category_general)
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    SettingRowWithIcon(
                        icon = CoreR.drawable.ic_proton_bell,
                        title = stringResource(id = R.string.settings_notifications_title),
                        onClick = onNotificationsClick,
                    )
                }

                SettingRowWithIcon(
                    icon = CoreR.drawable.ic_proton_circle_half_filled,
                    title = stringResource(R.string.settings_theme_title),
                    onClick = onThemeClick,
                    settingValue = viewState.theme.settingValueView,
                )

                SettingRowWithIcon(
                    icon = CoreR.drawable.ic_proton_grid_2,
                    title = stringResource(id = R.string.settings_change_icon_title),
                    trailingIcon = null,
                    trailingIconTint = false,
                    onClick = onIconChangeClick
                )
                SettingRowWithIcon(
                    icon = CoreR.drawable.ic_proton_mobile,
                    title = stringResource(id = R.string.settings_widget_title),
                    hasNewLabel = !viewState.isWidgetDiscovered,
                    onClick = onWidgetClick
                )
            }
            Category(
                modifier = horizontalItemPadding,
                stringResource(id = R.string.settings_category_support)
            ) {
                SettingRowWithIcon(
                    icon = CoreR.drawable.ic_proton_life_ring,
                    title = stringResource(id = R.string.settings_help_center_title),
                    trailingIcon = CoreR.drawable.ic_proton_arrow_out_square,
                    onClick = onOnHelpCenterClick,
                )
                SettingRowWithIcon(
                    icon = CoreR.drawable.ic_proton_bug,
                    onClick = onReportBugClick,
                    title = stringResource(id = R.string.settings_report_issue_title)
                )
                SettingRowWithIcon(
                    icon = CoreR.drawable.ic_proton_code,
                    onClick = onDebugLogsClick,
                    title = stringResource(id = R.string.settings_debug_logs_title)
                )
                if (viewState.showDebugTools) {
                    SettingRowWithIcon(
                        icon = CoreR.drawable.ic_proton_wrench,
                        title = "Debug tools",
                        onClick = onDebugToolsClick,
                    )
                }
            }
            Category(
                modifier = horizontalItemPadding,
                stringResource(id = R.string.settings_category_improve_proton)
            ) {
                SettingRowWithIcon(
                    icon = CoreR.drawable.ic_proton_users,
                    title = stringResource(id = R.string.settings_fight_censorship_title),
                    onClick = onHelpFightClick
                )
                SettingRowWithIcon(
                    icon = CoreR.drawable.ic_proton_star,
                    title = stringResource(id = R.string.settings_rate_us_title),
                    trailingIcon = CoreR.drawable.ic_proton_arrow_out_square,
                    onClick = onRateUsClick
                )

                Spacer(modifier = Modifier.size(8.dp))
            }
            if (viewState.showSignOut) {
                SettingRowWithIcon(
                    modifier = Modifier.padding(vertical = 8.dp),
                    icon = CoreR.drawable.ic_proton_arrow_in_to_rectangle,
                    title = stringResource(id = R.string.settings_sign_out),
                    onClick = onSignOutClick
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onThirdPartyLicensesClick() }
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(id = R.string.settings_app_version, viewState.versionName),
                    style = ProtonTheme.typography.captionWeak,
                )
                Text(
                    text = stringResource(id = R.string.settings_third_party_licenses),
                    color = ProtonTheme.colors.textAccent,
                    style = ProtonTheme.typography.captionNorm,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (viewState.buildInfo != null) {
                Text(
                    text = viewState.buildInfo,
                    style = ProtonTheme.typography.defaultSmallWeak,
                    modifier = horizontalItemPadding.padding(vertical = 8.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ColumnScope.FeatureCategory(
    modifier: Modifier = Modifier,
    viewState: SettingsViewModel.SettingsViewState,
    onNetShieldClick: () -> Unit,
    onNetShieldUpgrade: () -> Unit,
    onSplitTunnelClick: () -> Unit,
    onSplitTunnelUpgrade: () -> Unit,
    onAlwaysOnClick: () -> Unit,
) {
    Category(
        modifier = modifier.padding(start = 16.dp, end = 16.dp),
        stringResource(id = R.string.settings_category_features)
    ) {
        if (viewState.netShield != null) {
            SettingRowWithIcon(
                icon = viewState.netShield.iconRes,
                iconTint = false,
                title = stringResource(id = viewState.netShield.titleRes),
                settingValue = viewState.netShield.settingValueView,
                trailingIcon = R.drawable.vpn_plus_badge.takeIf { viewState.netShield.isRestricted },
                trailingIconTint = false,
                onClick = if (viewState.netShield.isRestricted) onNetShieldUpgrade else onNetShieldClick
            )
        }
        SettingRowWithIcon(
            icon = viewState.splitTunneling.iconRes,
            iconTint = false,
            title = stringResource(id = viewState.splitTunneling.titleRes),
            settingValue = viewState.splitTunneling.settingValueView,
            trailingIcon = R.drawable.vpn_plus_badge.takeIf { viewState.splitTunneling.isRestricted },
            trailingIconTint = false,
            onClick = if (viewState.splitTunneling.isRestricted)
                onSplitTunnelUpgrade else onSplitTunnelClick
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            SettingRowWithIcon(
                icon = R.drawable.ic_kill_switch,
                title = stringResource(id = R.string.settings_kill_switch_title),
                onClick = onAlwaysOnClick
            )
        }
    }
}

@Composable
private fun ColumnScope.AccountCategory(
    modifier: Modifier = Modifier,
    state: AccountSettingsViewState,
    showSingInOnAnotherDeviceQr: Boolean,
    onAccountClick: () -> Unit,
    onSignUpClick: () -> Unit,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
) {
    Category(
        modifier = modifier.padding(start = 16.dp, end = 16.dp),
        title = stringResource(id = R.string.settings_category_account)
    ) {
        AccountSettingsInfo(
            onAccountClicked = { onAccountClick() },
            onSignUpClicked = { onSignUpClick() },
            onSignInClicked = { onSignInClick() },
            onSignOutClicked = { onSignOutClick() },
            signOutButtonGone = true,
            initialCount = 1,
            state = state
        )
        if (showSingInOnAnotherDeviceQr) {
            SignInToAnotherDeviceItem(
                content = { label, onLogOut ->
                    SettingRowWithIcon(
                        icon = CoreR.drawable.ic_proton_qr_code,
                        title = label,
                        onClick = onLogOut
                    )
                }
            )
        }
    }
}

@Composable
private fun ColumnScope.Category(
    modifier: Modifier = Modifier,
    title: String,
    content: (@Composable ColumnScope.() -> Unit),
) {
    SettingsSectionHeading(title, modifier)
    content()
}

@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitleComposable: (@Composable () -> Unit)? = null,
    leadingComposable: (@Composable () -> Unit)? = null,
    trailingComposable: (@Composable () -> Unit)? = null,
    hasNewLabel: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    var baseModifier = modifier
        .fillMaxWidth()

    if (onClick != null) {
        baseModifier = baseModifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    }
    baseModifier = baseModifier.padding(vertical = 16.dp, horizontal = 16.dp)

    Row(
        modifier = baseModifier,
        verticalAlignment = if (subtitleComposable != null) Alignment.Top else Alignment.CenterVertically
    ) {
        if (leadingComposable != null) {
            Box(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .width(32.dp),
                contentAlignment = Alignment.Center
            ) {
                leadingComposable()
            }
        }
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = ProtonTheme.typography.defaultNorm,
                )
                subtitleComposable?.let {
                    it()
                }
            }
            if (hasNewLabel) {
                Spacer(Modifier.width(8.dp))
                LabelBadge(
                    text = stringResource(R.string.settings_new_label_badge),
                    textColor = ProtonTheme.colors.notificationWarning,
                    borderColor = ProtonTheme.colors.notificationWarning,
                )
            }
        }
        trailingComposable?.invoke()
    }
}

@Composable
fun SettingRowWithIcon(
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int,
    title: String,
    settingValue: SettingValue? = null,
    @DrawableRes trailingIcon: Int? = null,
    iconTint: Boolean = true,
    trailingIconTint: Boolean = true,
    hasNewLabel: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    SettingRow(
        leadingComposable = {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = null,
                tint = if (iconTint) ProtonTheme.colors.iconNorm else Color.Unspecified,
            )
        },
        trailingComposable = {
            trailingIcon?.let {
                Icon(
                    painter = painterResource(id = it),
                    contentDescription = null,
                    tint = if (trailingIconTint) ProtonTheme.colors.iconWeak else Color.Unspecified,
                    modifier = Modifier
                        .padding(end = 8.dp)
                )
            }
        },
        title = title,
        hasNewLabel = hasNewLabel,
        subtitleComposable = settingValue?.let {
            { SettingValueView(settingValue = it, modifier = Modifier.padding(top = 6.dp)) }
        },
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
private fun SettingValueView(
    settingValue: SettingValue,
    modifier: Modifier = Modifier,
) {
    when (settingValue) {
        is SettingValue.SettingOverrideValue ->
            OverrideSettingLabel(settingValue = settingValue, modifier = modifier)

        is SettingValue.SettingStringRes ->
            Text(
                text = stringResource(settingValue.subtitleRes),
                style = ProtonTheme.typography.defaultWeak,
                modifier = modifier,
            )

        is SettingValue.SettingText ->
            Text(
                text = settingValue.text,
                style = ProtonTheme.typography.defaultWeak,
                modifier = modifier,
            )
    }
}

@ProtonVpnPreview
@Composable
fun SettingRowWithIconPreview() {
    ProtonVpnPreview {
        SettingRowWithIcon(
            icon = R.drawable.vpn_plus_badge,
            title = "Netshield",
            settingValue = SettingValue.SettingStringRes(R.string.netshield_state_on),
            onClick = { }
        )
    }
}

@ProtonVpnPreview
@Composable
fun SettingRowWithOverridePreview() {
    ProtonVpnPreview {
        SettingRowWithIcon(
            icon = R.drawable.vpn_plus_badge,
            title = "Netshield",
            settingValue = SettingValue.SettingOverrideValue(
                connectIntentPrimaryLabel =
                    ConnectIntentPrimaryLabel.Profile(
                        "Profile name",
                        CountryId.sweden,
                        false,
                        ProfileIcon.Icon1,
                        ProfileColor.Color1
                    ),
                R.string.netshield_state_on
            ),
            onClick = { }
        )
    }
}

@ProtonVpnPreview
@Composable
fun SettingRowWithComposablesPreview() {
    ProtonVpnPreview {
        SettingRow(
            leadingComposable = {
                Text("A")
            },
            title = "User",
            subtitleComposable = {
                Text(
                    text = "user@mail.com",
                    style = ProtonTheme.typography.defaultNorm
                )
            },
            onClick = { }
        )
    }
}

@ProtonVpnPreview
@Composable
fun CategoryPreview() {
    ProtonVpnPreview {
        Column {
            Category(title = stringResource(id = R.string.settings_category_features)) {
                SettingRow(
                    leadingComposable = {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ProtonTheme.colors.brandNorm)
                        ) {
                            Text(
                                text = "AG",
                                style = ProtonTheme.typography.body1Regular,
                                color = Color.White,
                            )
                        }
                    },
                    title = stringResource(id = R.string.settings_netshield_title),
                    subtitleComposable = {
                        Text(
                            text = "On",
                            style = ProtonTheme.typography.defaultNorm
                        )
                    },
                )

                SettingRowWithIcon(
                    icon = CoreR.drawable.ic_proton_earth,
                    title = stringResource(id = R.string.settings_netshield_title),
                    settingValue = SettingValue.SettingStringRes(R.string.netshield_state_on)
                )
                SettingRowWithIcon(
                    icon = CoreR.drawable.ic_proton_earth,
                    title = stringResource(id = R.string.settings_split_tunneling_title),
                    settingValue = SettingValue.SettingStringRes(R.string.netshield_state_on)
                )
                SettingRowWithIcon(
                    icon = CoreR.drawable.ic_proton_mobile,
                    title = stringResource(id = R.string.settings_widget_title),
                    hasNewLabel = true,
                )
                SettingRowWithIcon(
                    icon = CoreR.drawable.ic_proton_earth,
                    title = stringResource(id = R.string.settings_kill_switch_title)
                )
            }
        }
    }
}

@ProtonVpnPreview
@Composable
fun AccountCategoryLoggedInPreview() {
    ProtonVpnPreview {
        Column {
            AccountCategory(
                state = AccountSettingsViewState.LoggedIn(
                    userId = UserId("userId"),
                    initials = "U",
                    displayName = "User",
                    email = "user@domain.com"
                ),
                onAccountClick = { },
                onSignUpClick = { },
                onSignInClick = { },
                onSignOutClick = { },
                showSingInOnAnotherDeviceQr = true
            )
        }
    }
}

@ProtonVpnPreview
@Composable
fun AccountCategoryCredentialLessPreview() {
    ProtonVpnPreview {
        Column {
            AccountCategory(
                state = AccountSettingsViewState.CredentialLess(UserId("userId")),
                onAccountClick = { },
                onSignUpClick = { },
                onSignInClick = { },
                onSignOutClick = { },
                showSingInOnAnotherDeviceQr = true
            )
        }
    }
}
