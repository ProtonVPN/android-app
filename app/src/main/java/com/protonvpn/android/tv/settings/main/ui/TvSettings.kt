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

package com.protonvpn.android.tv.settings.main.ui

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonVpnTvPreview
import com.protonvpn.android.base.ui.theme.backgroundSelected
import com.protonvpn.android.telemetry.UpgradeSource
import com.protonvpn.android.telemetry.UpgradeTrigger
import com.protonvpn.android.tv.dialogs.TvAlertDialog
import com.protonvpn.android.tv.ui.PlusBadgeTv
import com.protonvpn.android.tv.upsell.TvUpsellActivity
import com.protonvpn.android.tv.upsell.TvUpsellContent
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.R as CoreR

@Composable
fun TvSettingsRoute(
    modifier: Modifier = Modifier,
    viewModel: TvSettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val viewState = viewModel.viewState.collectAsStateWithLifecycle().value
    var showSignOutDialog by rememberSaveable { mutableStateOf(false) }

    if (viewState != null) {
        TvSettings(
            viewState = viewState,
            onClick = { item ->
                when (item.action) {
                    TvSettingAction.OpenUpsell -> item.setting.launchUpsell(context)
                    TvSettingAction.SignOut -> showSignOutDialog = true
                    is TvSettingAction.OpenActivity -> context.startActivity(Intent(context, item.action.activityClass))
                    null -> Unit
                }
            },
            modifier = modifier,
        )
    }

    if (showSignOutDialog) {
        val dismiss = { showSignOutDialog = false }
        TvAlertDialog(
            title = stringResource(R.string.tv_signout_dialog_title),
            description =
                if (viewModel.isConnected()) stringResource(R.string.tv_signout_dialog_description_connected)
                else null,
            confirmText = stringResource(R.string.tv_signout_dialog_ok),
            dismissText = stringResource(R.string.cancel),
            focusedButton = DialogInterface.BUTTON_NEGATIVE,
            onConfirm = {
                dismiss()
                viewModel.signOut()
            },
            onDismissRequest = dismiss,
        )
    }
}

@Composable
internal fun TvSettings(
    viewState: TvSettingsViewState,
    onClick: (TvSettingItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                // Horizontal padding leaves space for the focused row which is scaled up.
                .padding(horizontal = 20.dp)
                .padding(top = 60.dp, bottom = 24.dp)
                .width(400.dp)
        ) {
            viewState.settings.forEach { item ->
                TvSettingRow(
                    item = item,
                    onClick = { onClick(item) },
                )
            }
            Column {
                TvSettingsCaption(viewState)
                if (viewState.buildInfo != null) {
                    Text(
                        viewState.buildInfo,
                        style = ProtonTheme.typography.captionRegular,
                        color = ProtonTheme.colors.textWeak,
                    )
                }
            }
        }
    }
}

@Composable
private fun TvSettingsSelectableRow(
    onClick: () -> Unit,
    clickLabel: String?,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = ProtonTheme.colors.backgroundNorm,
            contentColor = ProtonTheme.colors.textNorm,
            focusedContainerColor = ProtonTheme.colors.backgroundSelected,
            focusedContentColor = ProtonTheme.colors.textInverted,
            pressedContainerColor = ProtonTheme.colors.backgroundSelected,
            pressedContentColor = ProtonTheme.colors.textInverted,
        ),
        // Only adds the label, the action comes from Surface's own semantics.
        modifier = modifier
            .fillMaxWidth()
            .semantics { if (clickLabel != null) onClick(label = clickLabel, action = null) },
    ) {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .heightIn(min = 56.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    // Vertical padding is smaller than in designs to account for text being larger
                    // than in Figma which doesn't support sp units.
                    // Min height is set on the container above.
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                content = content
            )
        }
    }
}

@Composable
private fun TvSettingRow(
    item: TvSettingItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRestricted = item.value == TvSettingValue.Restricted
    val clickLabel = if (isRestricted) stringResource(R.string.accessibility_action_upgrade) else null
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    TvSettingsSelectableRow(
        onClick = onClick,
        clickLabel = clickLabel,
        interactionSource = interactionSource,
        modifier = modifier,
    ) {
        val labelColor =
            if (isRestricted && !isFocused) ProtonTheme.colors.textWeak else LocalContentColor.current
        val textStyle = ProtonTheme.typography.body2Regular.copy(fontSize = 15.sp)

        Icon(
            painter = painterResource(item.setting.iconRes()),
            contentDescription = null,
            tint = if (isRestricted && !isFocused) ProtonTheme.colors.iconWeak else LocalContentColor.current,
        )
        Text(
            stringResource(item.setting.labelRes()),
            style = textStyle,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        when (val value = item.value) {
            is TvSettingValue.Text ->
                Text(
                    stringResource(value.res),
                    style = textStyle,
                    color = if (isFocused) LocalContentColor.current else ProtonTheme.colors.textWeak,
                    maxLines = 1,
                )

            TvSettingValue.Restricted -> PlusBadgeTv()
            TvSettingValue.None -> Unit
        }
    }
}

@Composable
private fun TvSettingsCaption(viewState: TvSettingsViewState, modifier: Modifier = Modifier) {
    val captionStyle = ProtonTheme.typography.captionRegular
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (viewState.accountEmail != null) {
                Text(
                    viewState.accountEmail,
                    style = ProtonTheme.typography.captionMedium,
                    color = ProtonTheme.colors.textNorm,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                viewState.planName ?: stringResource(R.string.accountFree),
                style = captionStyle,
                color = ProtonTheme.colors.textWeak,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            stringResource(R.string.settings_app_version, viewState.appVersion),
            style = captionStyle,
            color = ProtonTheme.colors.textWeak,
        )
    }
}

private fun TvSetting.launchUpsell(context: Context) {
    val (content, source) = when (this) {
        TvSetting.NetShield -> TvUpsellContent.NetShield to UpgradeSource.NETSHIELD
        TvSetting.SplitTunneling -> TvUpsellContent.SplitTunneling to UpgradeSource.SPLIT_TUNNELING
        TvSetting.Lan -> TvUpsellContent.LanConnections to UpgradeSource.ALLOW_LAN
        TvSetting.CustomDns -> TvUpsellContent.CustomDns to UpgradeSource.CUSTOM_DNS
        TvSetting.Protocol,
        TvSetting.AutoConnect,
        TvSetting.IPv6,
        TvSetting.ReportIssue,
        TvSetting.SignOut -> return
    }
    TvUpsellActivity.launch(context, content, source, UpgradeTrigger.SETTINGS)
}

@StringRes
private fun TvSetting.labelRes(): Int = when (this) {
    TvSetting.NetShield -> R.string.settings_netshield_title
    TvSetting.SplitTunneling -> R.string.tv_card_split_tunneling_label
    TvSetting.Protocol -> R.string.tv_card_protocol_label
    TvSetting.AutoConnect -> R.string.settings_autoconnect_title
    TvSetting.Lan -> R.string.tv_card_lan_connections_label
    TvSetting.CustomDns -> R.string.settings_custom_dns_title
    TvSetting.IPv6 -> R.string.settings_advanced_ipv6_title
    TvSetting.ReportIssue -> R.string.drawerReportProblem
    TvSetting.SignOut -> R.string.tv_signout_label
}

@DrawableRes
private fun TvSetting.iconRes(): Int = when (this) {
    TvSetting.NetShield -> CoreR.drawable.ic_proton_shield_filled
    TvSetting.SplitTunneling -> CoreR.drawable.ic_proton_arrows_swap_right
    TvSetting.Protocol -> CoreR.drawable.ic_proton_shield_2_bolt
    TvSetting.AutoConnect -> CoreR.drawable.ic_proton_power_off
    TvSetting.Lan -> CoreR.drawable.ic_proton_arrow_right_arrow_left
    TvSetting.CustomDns -> CoreR.drawable.ic_proton_servers
    TvSetting.IPv6 -> CoreR.drawable.ic_proton_globe
    TvSetting.ReportIssue -> CoreR.drawable.ic_proton_bug
    TvSetting.SignOut -> CoreR.drawable.ic_proton_arrow_out_from_rectangle
}

@ProtonVpnTvPreview
@Composable
private fun TvSettingsPreview() {
    ProtonVpnTvPreview {
        TvSettings(
            viewState = TvSettingsViewState(
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
            ),
            onClick = {},
        )
    }
}
