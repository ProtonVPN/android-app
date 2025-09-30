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
import androidx.compose.ui.unit.Dp
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
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.visibility
import com.protonvpn.android.R
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentViewState
import com.protonvpn.android.redesign.vpn.ui.label
import com.protonvpn.android.widget.WidgetRecent
import com.protonvpn.android.widget.WidgetViewState
import com.protonvpn.android.widget.WidgetVpnStatus
import com.protonvpn.android.widget.ui.GlanceIntentLabelSize.Big
import com.protonvpn.android.widget.ui.GlanceIntentLabelSize.Medium
import com.protonvpn.android.widget.ui.GlanceIntentLabelSize.Recent
import com.protonvpn.android.widget.ui.GlanceIntentLabelSize.Small
import kotlin.math.min
import me.proton.core.presentation.R as CoreR

@Composable
fun WidgetViewState.widgetBackground(): Int = when(this) {
    is WidgetViewState.NeedLogin -> ProtonGlanceTheme.resources.widgetBackgroundNeedsLogin
    is WidgetViewState.LoggedIn -> ProtonGlanceTheme.resources.widgetBackgroundLoggedIn
    is WidgetViewState.NoServersAvailable -> ProtonGlanceTheme.resources.widgetBackgroundUnavailable
}

@Composable
fun WidgetViewState.LoggedIn.statusGradient(): Int? = when (vpnStatus) {
    WidgetVpnStatus.Connected -> ProtonGlanceTheme.resources.widgetGradientConnected
    WidgetVpnStatus.Connecting,
    WidgetVpnStatus.WaitingForNetwork -> ProtonGlanceTheme.resources.widgetGradientConnecting
    WidgetVpnStatus.Disconnected ->ProtonGlanceTheme.resources.widgetGradientDisconnected
}

@Composable
fun GlanceVpnStatus(status: WidgetVpnStatus, wide: Boolean, small: Boolean, modifier: GlanceModifier = GlanceModifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(top = 2.dp).fillMaxWidth()
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
        val iconSize = if (small) 20.dp else 22.dp
        if (iconRes != null) {
            Image(
                ImageProvider(iconRes),
                colorFilter = ColorFilter.tint(color),
                contentDescription = null,
                modifier = GlanceModifier.size(iconSize)
            )
        } else {
            CircularProgressIndicator(
                color = color,
                modifier = GlanceModifier.size(iconSize)
            )
        }
        if (wide) {
            val statusStyle = if (small) ProtonGlanceTheme.typography.statusSmall else ProtonGlanceTheme.typography.status
            Text(
                when (status) {
                    WidgetVpnStatus.Connected -> glanceStringResource(R.string.vpn_status_connected)
                    WidgetVpnStatus.Disconnected -> glanceStringResource(R.string.widget_status_unprotected)
                    WidgetVpnStatus.Connecting -> glanceStringResource(R.string.widget_status_connecting)
                    WidgetVpnStatus.WaitingForNetwork -> glanceStringResource(R.string.error_vpn_waiting_for_network)
                },
                style = statusStyle.copy(color = color),
                modifier = GlanceModifier.padding(start = 8.dp, top = 4.dp)
            )
        }
        Spacer(modifier = GlanceModifier.defaultWeight())
    }
}

@Composable
fun GlanceConnectIntent(
    state: ConnectIntentViewState,
    dimensions: GlanceIntentDimensions,
    center: Boolean,
    modifier: GlanceModifier = GlanceModifier,
    fakeContent: Boolean = false,
) {
    val iconModifier = GlanceModifier.size(dimensions.iconSize)
    @Composable
    fun Icon() {
        if (fakeContent) {
            Box(iconModifier) {}
        } else {
            ConnectIntentGlanceIcon(state.primaryLabel, iconModifier)
        }
    }

    @Composable
    fun Label(modifier: GlanceModifier = GlanceModifier) {
        if (fakeContent) {
            EmptyFullSizeLabel(dimensions.labelSize)
        } else {
            ConnectIntentGlanceLabels(
                state,
                dimensions.labelSize,
                forceMaxHeight = dimensions.forceMaxHeight,
                center = center,
                modifier = modifier
            )
        }
    }

    if (dimensions.horizontal) {
        Row(
            modifier = modifier,
            verticalAlignment = if (center) Alignment.CenterVertically else Alignment.Top
        ) {
            if (dimensions.showIcon) {
                Icon()
                Spacer(modifier = GlanceModifier.width(8.dp))
            }
            val topPadding = if (center) 0.dp else when (dimensions.labelSize) {
                Small -> 1.dp
                Medium -> 2.dp
                Big -> 3.dp
                Recent -> 1.dp
            }
            Label(GlanceModifier.padding(top = topPadding))
        }
    } else {
        Column(
            modifier = modifier,
            horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start
        ) {
            if (dimensions.showIcon) {
                Icon()
                Spacer(modifier = GlanceModifier.height(8.dp))
            }
            Label()
        }
    }
}

@Composable
fun GlanceConnectButton(action: Action, vpnStatus: WidgetVpnStatus, modifier: GlanceModifier = GlanceModifier.fillMaxWidth()) {
    val text = when(vpnStatus) {
        WidgetVpnStatus.Connected -> R.string.disconnect
        WidgetVpnStatus.WaitingForNetwork,
        WidgetVpnStatus.Connecting -> R.string.cancel
        WidgetVpnStatus.Disconnected ->  R.string.connect

    }
    GlanceButton(text, action, secondary = vpnStatus != WidgetVpnStatus.Disconnected, modifier = modifier)
}

enum class GlanceIntentLabelSize {
    Small, Medium, Big, Recent;

    val mainTextSize: TextStyle
        @Composable get() = when (this) {
            Small -> ProtonGlanceTheme.typography.small
            Medium -> ProtonGlanceTheme.typography.medium
            Big -> ProtonGlanceTheme.typography.big
            Recent -> ProtonGlanceTheme.typography.small
        }

    val secondaryTextSize: TextStyle
        @Composable get() = when (this) {
            Small -> ProtonGlanceTheme.typography.smallSecondary
            Medium -> ProtonGlanceTheme.typography.mediumSecondary
            Big -> ProtonGlanceTheme.typography.bigSecondary
            Recent -> ProtonGlanceTheme.typography.mediumSecondary
        }
}

data class GlanceIntentDimensions(
    val labelSize: GlanceIntentLabelSize,
    val horizontal: Boolean,
    val showIcon: Boolean,
    val forceMaxHeight: Boolean
) {
    val iconSize: Dp get() = when (labelSize) {
        Small, Medium, Recent -> 24.dp
        Big -> 30.dp
    }
}

@Composable
fun ConnectIntentGlanceLabels(
    state: ConnectIntentViewState,
    size: GlanceIntentLabelSize,
    forceMaxHeight: Boolean,
    center: Boolean,
    modifier: GlanceModifier = GlanceModifier
) {
    Box(modifier) {
        val textAlign = if (center) TextAlign.Center else TextAlign.Start
        Column(
            horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start
        ) {
            Text(
                state.primaryLabel.label(),
                maxLines = if (state.secondaryLabel == null) 2 else 1,
                style = size.mainTextSize.copy(textAlign = textAlign),
            )
            if (state.secondaryLabel != null) {
                Text(
                    state.secondaryLabel.label(plainText = true).text,
                    maxLines = 1,
                    style = size.secondaryTextSize.copy(textAlign = textAlign),
                )
            }
        }
        // Invisible view forcing fixed max height of 2 lines of default text size.
        if (forceMaxHeight)
            EmptyFullSizeLabel(size, modifier.visibility(Visibility.Invisible))
    }
}

// Fake view used to force max size label for layouts.
@Composable
private fun EmptyFullSizeLabel(size: GlanceIntentLabelSize, modifier: GlanceModifier = GlanceModifier) {
    Text(
        modifier = modifier,
        text = "\n",
        style = size.mainTextSize,
        maxLines = 2,
    )
}

@Composable
fun ColumnScope.GlanceRecents(recents: List<WidgetRecent>, maxColumns: Int, maxRows: Int) {
    if (recents.isEmpty()) return

    val recentCount = recents.size
    val rows = min(maxRows, (maxColumns + recentCount - 1) / maxColumns)
    val recentsRows = recents
        .take(rows * maxColumns)
        .chunked(maxColumns)
    val rowModifier = GlanceModifier.fillMaxWidth()
    recentsRows.forEachIndexed { rowIdx, row ->
        if (rowIdx > 0)
            Spacer(modifier = GlanceModifier.height(8.dp))
        GlanceRecentsRow(row, maxColumns, rowModifier)
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
            Box(GlanceModifier.defaultWeight()) {
                val recent = recents.getOrNull(i)
                if (recent != null) {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .background(ImageProvider(ProtonGlanceTheme.resources.buttonBackgroundSecondary))
                            .clickable(recent.action)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        @Composable
                        fun Intent(
                            invisible: Boolean = false,
                            forceMaxHeight: Boolean = false,
                        ) {
                            val visibilityModifier =
                                if (invisible) GlanceModifier.visibility(Visibility.Invisible)
                                else GlanceModifier
                            GlanceConnectIntent(
                                recent.connectIntentViewState,
                                dimensions = GlanceIntentDimensions(
                                    labelSize = Recent,
                                    horizontal = false,
                                    forceMaxHeight = forceMaxHeight,
                                    showIcon = true,
                                ),
                                fakeContent = invisible,
                                center = true,
                                modifier = GlanceModifier.fillMaxWidth().then(visibilityModifier)
                            )
                        }

                        // Invisible intent forcing box to fit max size intent.
                        Intent(invisible = true, forceMaxHeight = true)

                        // Actual intent view
                        Intent()
                    }
                }
            }
        }
    }
}
