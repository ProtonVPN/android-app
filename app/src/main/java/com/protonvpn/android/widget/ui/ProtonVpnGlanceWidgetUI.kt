/*
 * Copyright (c) 2025 Proton AG
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

package com.protonvpn.android.widget.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.Visibility
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.visibility
import com.protonvpn.android.R
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentPrimaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentViewState
import com.protonvpn.android.redesign.vpn.ui.label
import com.protonvpn.android.widget.WidgetRecent
import com.protonvpn.android.widget.WidgetViewState
import com.protonvpn.android.widget.WidgetVpnStatus
import kotlin.math.min
import me.proton.core.presentation.R as CoreR

@Composable
fun WidgetViewState.widgetBackground(): Int = when(this) {
    is WidgetViewState.NeedLogin ->
        ProtonGlanceTheme.resources.widgetBackgroundNeedsLogin
    is WidgetViewState.LoggedIn -> when (vpnStatus) {
        WidgetVpnStatus.Connected -> ProtonGlanceTheme.resources.widgetBackgroundConnected
        WidgetVpnStatus.Connecting,
        WidgetVpnStatus.WaitingForNetwork -> ProtonGlanceTheme.resources.widgetBackgroundConnecting
        WidgetVpnStatus.Disconnected ->ProtonGlanceTheme.resources.widgetBackgroundDisconnected
    }
}

@Composable
fun ConnectWithIntent(viewState: WidgetViewState.LoggedIn, showConnecting: Boolean, wide: Boolean, modifier: GlanceModifier) {
    Column(modifier) {
        Spacer(modifier = GlanceModifier.defaultWeight())
        GlanceConnectIntent(
            viewState.connectCard,
            horizontal = wide,
            isConnecting = showConnecting && viewState.isConnecting,
            modifier = GlanceModifier.padding(horizontal = 8.dp)
        )
        Spacer(modifier = GlanceModifier.defaultWeight())
        GlanceConnectButton(viewState.connectCardAction, viewState.vpnStatus)
    }
}

@Composable
fun GlanceVpnStatus(status: WidgetVpnStatus, wide: Boolean, modifier: GlanceModifier = GlanceModifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(top = 4.dp).fillMaxWidth()
    ) {
        Spacer(modifier = GlanceModifier.defaultWeight())
        val (color, iconRes) = when (status) {
            WidgetVpnStatus.Connected ->
                ProtonGlanceTheme.colors.protected to CoreR.drawable.ic_proton_lock_filled
            WidgetVpnStatus.Disconnected ->
                ProtonGlanceTheme.colors.unprotected to CoreR.drawable.ic_proton_lock_open_filled_2
            else ->
                ProtonGlanceTheme.colors.textNorm to null
        }
        if (iconRes != null) {
            Image(
                ImageProvider(iconRes),
                colorFilter = ColorFilter.tint(color),
                contentDescription = null,
                modifier = GlanceModifier.size(20.dp)
            )
        } else {
            CircularProgressIndicator(
                color = color,
                modifier = GlanceModifier.size(20.dp)
            )
        }
        if (wide) {
            Text(
                when (status) {
                    WidgetVpnStatus.Connected -> glanceStringResource(R.string.connected)
                    WidgetVpnStatus.Disconnected -> glanceStringResource(R.string.widget_status_unprotected)
                    WidgetVpnStatus.Connecting -> glanceStringResource(R.string.widget_status_connecting)
                    WidgetVpnStatus.WaitingForNetwork -> glanceStringResource(R.string.error_vpn_waiting_for_network)
                },
                style = ProtonGlanceTheme.typography.status.copy(color = color),
                modifier = GlanceModifier.padding(start = 8.dp, top = 4.dp)
            )
        }
        Spacer(modifier = GlanceModifier.defaultWeight())
    }
}

@Composable
private fun GlanceConnectIntent(
    state: ConnectIntentViewState,
    horizontal: Boolean,
    isConnecting: Boolean = false,
    modifier: GlanceModifier = GlanceModifier
) {
    if (horizontal) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlanceConnectIntentIconOrSpinner(state.primaryLabel, isConnecting)
            Spacer(modifier = GlanceModifier.width(8.dp))
            ConnectIntentGlanceLabels(state, forceMaxHeight = false)
        }
    } else {
        Column(modifier) {
            GlanceConnectIntentIconOrSpinner(state.primaryLabel, isConnecting)
            Spacer(modifier = GlanceModifier.height(8.dp))
            ConnectIntentGlanceLabels(state, forceMaxHeight = true)
        }
    }
}

@Composable
private fun GlanceConnectIntentIconOrSpinner(label: ConnectIntentPrimaryLabel, isConnecting: Boolean) {
    if (isConnecting) {
        CircularProgressIndicator(
            color = ProtonGlanceTheme.colors.textNorm,
            modifier = GlanceModifier.size(24.dp)
        )
    } else {
        ConnectIntentGlanceIcon(label)
    }
}

@Composable
fun GlanceConnectButton(action: Action, vpnStatus: WidgetVpnStatus, modifier: GlanceModifier = GlanceModifier.fillMaxWidth()) {
    GlanceButton(
        if (vpnStatus != WidgetVpnStatus.Disconnected) R.string.disconnect else R.string.connect,
        action,
        secondary = vpnStatus != WidgetVpnStatus.Disconnected,
        modifier = modifier
    )
}

@Composable
fun ConnectIntentGlanceLabels(state: ConnectIntentViewState, forceMaxHeight: Boolean, modifier: GlanceModifier = GlanceModifier) {
    Box(modifier) {
        Column {
            Text(
                state.primaryLabel.label(),
                maxLines = if (state.secondaryLabel == null) 2 else 1,
                style = ProtonGlanceTheme.typography.defaultNorm,
            )
            if (state.secondaryLabel != null) {
                Text(
                    state.secondaryLabel.label(plainText = true).text,
                    maxLines = 1,
                    style = ProtonGlanceTheme.typography.secondary,
                )
            }
        }
        // Invisible view forcing fixed max height of 2 lines of default text size.
        if (forceMaxHeight) {
            Text(
                text = "\n",
                style = ProtonGlanceTheme.typography.defaultNorm,
                maxLines = 2,
                modifier = GlanceModifier.visibility(Visibility.Invisible)
            )
        }
    }
}

@Composable
fun ColumnScope.GlanceRecents(recents: List<WidgetRecent>, maxColumns: Int, maxRows: Int) {
    if (recents.isEmpty()) return

    val recentCount = recents.size
    val columns = min(maxColumns, recentCount)
    val rows = min(maxRows, (columns + recentCount - 1) / columns)
    val recentsRows = recents
        .take(rows * columns)
        .chunked(columns)
    val rowModifier = GlanceModifier.fillMaxWidth().defaultWeight()
    recentsRows.forEachIndexed { rowIdx, row ->
        if (rowIdx > 0)
            Spacer(modifier = GlanceModifier.height(8.dp))
        GlanceRecentsRow(row, columns, rowModifier)
    }
}

@Composable
private fun GlanceRecentsRow(
    recents: List<WidgetRecent>,
    columnsCount: Int,
    modifier: GlanceModifier = GlanceModifier
) {
    if (recents.isEmpty()) return

    Row(modifier) {
        repeat(columnsCount) { i ->
            if (i > 0)
                Spacer(modifier = GlanceModifier.width(8.dp))
            Box(GlanceModifier.fillMaxHeight().defaultWeight()) {
                val recent = recents.getOrNull(i)
                if (recent != null) {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .background(ImageProvider(ProtonGlanceTheme.resources.buttonBackgroundSecondary))
                            .clickable(recent.action)
                            .padding(8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        GlanceConnectIntent(recent.connectIntentViewState, horizontal = false)
                    }
                }
            }
        }
    }
}
