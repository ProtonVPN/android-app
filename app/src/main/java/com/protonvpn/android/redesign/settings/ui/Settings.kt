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
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
import android.provider.Settings.EXTRA_APP_PACKAGE
import android.provider.Settings.EXTRA_CHANNEL_ID
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.BuildConfig
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.theme.VpnTheme
import com.protonvpn.android.redesign.base.ui.largeScreenContentPadding
import com.protonvpn.android.redesign.settings.ui.nav.SubSettingsScreen
import com.protonvpn.android.redesign.vpn.ui.label
import com.protonvpn.android.ui.drawer.LogActivity
import com.protonvpn.android.ui.drawer.bugreport.DynamicReportActivity
import com.protonvpn.android.ui.planupgrade.CarouselUpgradeDialogActivity
import com.protonvpn.android.ui.planupgrade.UpgradeNetShieldHighlightsFragment
import com.protonvpn.android.ui.planupgrade.UpgradeSplitTunnelingHighlightsFragment
import com.protonvpn.android.ui.planupgrade.UpgradeVpnAcceleratorHighlightsFragment
import com.protonvpn.android.ui.settings.OssLicensesActivity
import com.protonvpn.android.ui.settings.SettingsAlwaysOnActivity
import com.protonvpn.android.ui.settings.SettingsTelemetryActivity
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
    onNavigateToSubSetting: (SubSettingsScreen.Type) -> Unit
) {
    val viewModel = hiltViewModel<SettingsViewModel>()
    val viewState = viewModel.viewState.collectAsStateWithLifecycle(initialValue = null).value

    val accountSettingsViewModel = hiltViewModel<AccountSettingsViewModel>()
    val accountSettingsViewState = accountSettingsViewModel.state.collectAsStateWithLifecycle().value

    if (viewState == null || accountSettingsViewState == AccountSettingsViewState.Hidden) {
        // Return a composable even when viewState == null to avoid transition glitches
        Box(modifier = Modifier.fillMaxSize()) {}
        return
    }

    val context = LocalContext.current

    CompositionLocalProvider(
        LocalProductMetricsDelegateOwner provides ProductMetricsDelegateOwner(accountSettingsViewModel)
    ) {
        SettingsView(
            viewState = viewState,
            accountSettingsViewState = accountSettingsViewState,
            onSignUpClick = onSignUpClick,
            onSignInClick = onSignInClick,
            onSignOutClick = onSignOutClick,
            onAccountClick = {
                if (viewState.accountScreenEnabled)
                    onNavigateToSubSetting(SubSettingsScreen.Type.Account)
            },
            onNetShieldClick = {
                onNavigateToSubSetting(SubSettingsScreen.Type.NetShield)
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                    context.startActivity(Intent(context, SettingsAlwaysOnActivity::class.java))
            },
            onProtocolClick = {
                onNavigateToSubSetting(SubSettingsScreen.Type.Protocol)
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
                context.startActivity(Intent(context, DynamicReportActivity::class.java))
            },
            onDebugLogsClick = {
                context.startActivity(Intent(context, LogActivity::class.java))
            },
            onHelpFightClick = {
                context.startActivity(Intent(context, SettingsTelemetryActivity::class.java))
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
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollapsibleToolbarScaffold(
    modifier: Modifier = Modifier,
    @StringRes titleResId: Int,
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    toolbarActions: @Composable RowScope.() -> Unit = {},
    toolbarAdditionalContent: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val isCollapsed = remember { derivedStateOf { scrollBehavior.state.collapsedFraction > 0.5 } }

    val topAppBarElementColor = if (isCollapsed.value) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onPrimary
    }

    val collapsedTextSize = 16
    val expandedTextSize = 28
    val topAppBarTextSize =
        (collapsedTextSize + (expandedTextSize - collapsedTextSize) * (1 - scrollBehavior.state.collapsedFraction)).sp

    val expandedColor = ProtonTheme.colors.backgroundNorm
    val collapsedColor = ProtonTheme.colors.backgroundSecondary
    val topAppBarColor by remember {
        derivedStateOf {
            lerp(expandedColor, collapsedColor, scrollBehavior.state.collapsedFraction)
        }
    }
    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(color = topAppBarColor)) {
                MediumTopAppBar(
                    title = {
                            Text(
                                text = stringResource(id = titleResId),
                                style = ProtonTheme.typography.hero,
                                fontSize = topAppBarTextSize
                            )
                    },
                    actions = toolbarActions,
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = topAppBarColor,
                        scrolledContainerColor = topAppBarColor,
                        navigationIconContentColor = topAppBarElementColor,
                        titleContentColor = topAppBarElementColor,
                        actionIconContentColor = topAppBarElementColor,
                    ),
                    scrollBehavior = scrollBehavior
                )
                toolbarAdditionalContent()
            }
        },
        contentWindowInsets = contentWindowInsets,
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        content = content
    )
}

@Composable
private fun SettingsView(
    modifier: Modifier = Modifier,
    viewState: SettingsViewModel.SettingsViewState,
    accountSettingsViewState: AccountSettingsViewState,
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
    onOnHelpCenterClick: () -> Unit,
    onReportBugClick: () -> Unit,
    onDebugLogsClick: () -> Unit,
    onHelpFightClick: () -> Unit,
    onRateUsClick: () -> Unit,
    onThirdPartyLicensesClick: () -> Unit,
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
            AccountCategory(
                state = accountSettingsViewState,
                onAccountClick = onAccountClick,
                onSignUpClick = onSignUpClick,
                onSignInClick = onSignInClick,
                onSignOutClick = onSignOutClick
            )
            FeatureCategory(
                viewState = viewState,
                onNetShieldClick = onNetShieldClick,
                onNetShieldUpgrade = onNetShieldUpgradeClick,
                onSplitTunnelClick = onSplitTunnelClick,
                onSplitTunnelUpgrade = onSplitTunnelUpgrade,
                onIconChangeClick = onIconChangeClick,
                onAlwaysOnClick = onAlwaysOnClick
            )
            Category(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                stringResource(id = R.string.settings_connection_category)
            ) {
                viewState.defaultConnection?.let {
                    SettingRowWithIcon(
                        icon = it.iconRes,
                        iconTint = true,
                        title = stringResource(id = it.titleRes),
                        subtitle = it.predefinedTitle?.let { stringResource(id = it) } ?: it.recentLabel?.label(),
                        onClick = onDefaultConnectionClick,
                    )
                }
                SettingRowWithIcon(
                    icon = viewState.protocol.iconRes,
                    iconTint = true,
                    title = stringResource(id = viewState.protocol.titleRes),
                    onClick = onProtocolClick,
                    subtitle = stringResource(id = viewState.protocol.subtitleRes)
                )
                SettingRowWithIcon(
                    icon = viewState.vpnAccelerator.iconRes,
                    title = stringResource(id = viewState.vpnAccelerator.titleRes),
                    onClick = if (viewState.vpnAccelerator.isRestricted)
                        onVpnAcceleratorUpgrade else onVpnAcceleratorClick,
                    trailingIcon = viewState.vpnAccelerator.upgradeIconRes,
                    trailingIconTint = false,
                    subtitle = stringResource(id = viewState.vpnAccelerator.subtitleRes)
                )
                SettingRowWithIcon(
                    icon = CoreR.drawable.ic_proton_sliders,
                    title = stringResource(id = R.string.settings_advanced_settings_title),
                    onClick = onAdvancedSettingsClick,
                )
            }

            Category(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp),
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
                    icon = CoreR.drawable.ic_proton_grid_2,
                    title = stringResource(id = R.string.settings_change_icon_title),
                    subtitle = null,
                    trailingIcon = null,
                    trailingIconTint = false,
                    onClick = onIconChangeClick
                )
            }
            Category(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp),
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
            }
            Category(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp),
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
                    text = stringResource(
                        id = R.string.settings_app_version,
                        BuildConfig.VERSION_NAME
                    ),
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
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
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
    onIconChangeClick: () -> Unit,
    onAlwaysOnClick: () -> Unit,
) {
    Category(
        modifier = modifier.padding(start = 16.dp, end = 16.dp),
        stringResource(id = R.string.settings_category_features)
    ) {
        if (viewState.netShield != null) {
            SettingRowWithIcon(
                icon = viewState.netShield.iconRes,
                title = stringResource(id = viewState.netShield.titleRes),
                subtitle = stringResource(id = viewState.netShield.subtitleRes),
                trailingIcon = viewState.netShield.upgradeIconRes,
                trailingIconTint = false,
                onClick = if (viewState.netShield.isRestricted) onNetShieldUpgrade else onNetShieldClick
            )
        }
        SettingRowWithIcon(
            icon = viewState.splitTunneling.iconRes,
            title = stringResource(id = viewState.splitTunneling.titleRes),
            subtitle = stringResource(id = viewState.splitTunneling.subtitleRes),
            trailingIcon = viewState.splitTunneling.upgradeIconRes,
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
    subtitle: String? = null,
    leadingComposable: (@Composable () -> Unit)? = null,
    trailingComposable: (@Composable () -> Unit)? = null,
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
        verticalAlignment = if (subtitle != null) Alignment.Top else Alignment.CenterVertically
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
        Column(
            modifier = Modifier
                .weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = ProtonTheme.typography.defaultNorm,
            )
            subtitle?.let {
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = it,
                    style = ProtonTheme.typography.defaultWeak
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
    subtitle: String? = null,
    @DrawableRes trailingIcon: Int? = null,
    iconTint: Boolean = false,
    trailingIconTint: Boolean = true,
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
        subtitle = subtitle,
        onClick = onClick,
        modifier = modifier
    )
}

@Preview
@Composable
fun SettingRowWithIconPreview() {
    VpnTheme(isDark = true) {
        SettingRowWithIcon(
            icon = R.drawable.vpn_plus_badge,
            title = "Netshield",
            subtitle = "On",
            onClick = { }
        )
    }
}

@Preview
@Composable
fun SettingRowWithComposablesPreview() {
    VpnTheme(isDark = true) {
        SettingRow(
            leadingComposable = {
                Text("A")
            },
            title = "User",
            subtitle = "user@mail.com",
            onClick = { }
        )
    }
}

@Preview
@Composable
fun CategoryPreview() {
    VpnTheme(isDark = true) {
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
                                style = ProtonTheme.typography.defaultNorm
                            )
                        }
                    },
                    title = stringResource(id = R.string.settings_netshield_title),
                    subtitle = "On"
                )

                SettingRowWithIcon(
                    icon = CoreR.drawable.ic_proton_earth,
                    title = stringResource(id = R.string.settings_netshield_title),
                    subtitle = "On"
                )
                SettingRowWithIcon(
                    icon = CoreR.drawable.ic_proton_earth,
                    title = stringResource(id = R.string.settings_split_tunneling_title),
                    subtitle = "On"
                )
                SettingRowWithIcon(
                    icon = CoreR.drawable.ic_proton_earth,
                    title = stringResource(id = R.string.settings_kill_switch_title)
                )
            }
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun AccountCategoryLoggedInPreview() {
    VpnTheme(isDark = true) {
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
            )
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun AccountCategoryCredentialLessPreview() {
    VpnTheme(isDark = true) {
        Column {
            AccountCategory(
                state = AccountSettingsViewState.CredentialLess(UserId("userId")),
                onAccountClick = { },
                onSignUpClick = { },
                onSignInClick = { },
                onSignOutClick = { },
            )
        }
    }
}
