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

package com.protonvpn.android.tv.vpn.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonVpnTvPreview
import com.protonvpn.android.base.ui.vpnGreen
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.R as CoreR

private val StatusIconSize = 28.dp

@Composable
fun VpnStatusTv(
    state: VpnStatusTvViewState,
    modifier: Modifier = Modifier,
) {
    // The location chip will be added below the title.
    Column(modifier.padding(horizontal = 10.dp)) {
        VpnStatusTitleTv(state)
    }
}

@Composable
private fun VpnStatusTitleTv(
    state: VpnStatusTvViewState,
    modifier: Modifier = Modifier,
) {
    val textColor = when (state) {
        VpnStatusTvViewState.Unprotected -> ProtonTheme.colors.notificationError
        VpnStatusTvViewState.Connecting -> ProtonTheme.colors.textNorm
        VpnStatusTvViewState.Protected -> ProtonTheme.colors.vpnGreen
    }
    val textRes = when (state) {
        VpnStatusTvViewState.Unprotected -> R.string.tv_vpn_status_unprotected
        VpnStatusTvViewState.Connecting -> R.string.tv_vpn_status_connecting
        VpnStatusTvViewState.Protected -> R.string.tv_vpn_status_protected
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (state == VpnStatusTvViewState.Connecting) 5.dp else 12.dp),
        modifier = modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        when (state) {
            VpnStatusTvViewState.Unprotected ->
                StatusIcon(CoreR.drawable.ic_proton_lock_open_filled_2, textColor)
            VpnStatusTvViewState.Connecting ->
                CircularProgressIndicator(
                    color = ProtonTheme.colors.iconNorm,
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .padding(2.dp)
                        .size(StatusIconSize - 4.dp)
                )
            VpnStatusTvViewState.Protected ->
                StatusIcon(CoreR.drawable.ic_proton_lock_filled, textColor)
        }
        Text(
            stringResource(textRes),
            style = ProtonTheme.typography.hero,
            color = textColor,
        )
    }
}

@Composable
private fun StatusIcon(iconRes: Int, tint: Color, modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(StatusIconSize),
    )
}

@ProtonVpnTvPreview
@Composable
private fun VpnStatusTvPreview() {
    ProtonVpnTvPreview {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            VpnStatusTvViewState.entries.forEach { VpnStatusTv(it) }
        }
    }
}
