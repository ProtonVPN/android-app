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

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.AnimateTextMasking
import com.protonvpn.android.base.ui.ProtonVpnTvPreview
import com.protonvpn.android.base.ui.vpnGreen
import com.protonvpn.android.tv.buttons.TvSolidButton
import com.protonvpn.android.vpn.ui.vpnLocationText
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.R as CoreR

private val StatusIconSize = 28.dp

@Composable
fun VpnStatusTv(
    state: VpnStatusTvViewState,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
    actionFocusRequester: FocusRequester = FocusRequester.Default,
    actionStartFocusRequester: FocusRequester = FocusRequester.Default,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = modifier.padding(horizontal = 10.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            VpnStatusTitleTv(state.status)
            LocationChipTv(
                state.location,
                isMasking = state.status == VpnStatusTvViewState.Status.Connecting,
            )
        }
        // TODO: use the V6 button style once TV theming is done.
        TvSolidButton(
            text = stringResource(state.status.actionLabel()),
            onClick = onActionClick,
            // TvSolidButton fills the available width, the status button should wrap its text.
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .focusRequester(actionFocusRequester)
                .focusProperties { start = actionStartFocusRequester },
        )
    }
}

@StringRes
private fun VpnStatusTvViewState.Status.actionLabel(): Int = when (this) {
    VpnStatusTvViewState.Status.Unprotected -> R.string.tv_vpn_status_action_connect_fastest
    VpnStatusTvViewState.Status.Connecting -> R.string.cancel
    VpnStatusTvViewState.Status.Protected -> R.string.disconnect
}

@Composable
private fun VpnStatusTitleTv(
    status: VpnStatusTvViewState.Status,
    modifier: Modifier = Modifier,
) {
    val textColor = when (status) {
        VpnStatusTvViewState.Status.Unprotected -> ProtonTheme.colors.notificationError
        VpnStatusTvViewState.Status.Connecting -> ProtonTheme.colors.textNorm
        VpnStatusTvViewState.Status.Protected -> ProtonTheme.colors.vpnGreen
    }
    val textRes = when (status) {
        VpnStatusTvViewState.Status.Unprotected -> R.string.tv_vpn_status_unprotected
        VpnStatusTvViewState.Status.Connecting -> R.string.tv_vpn_status_connecting
        VpnStatusTvViewState.Status.Protected -> R.string.tv_vpn_status_protected
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement =
            Arrangement.spacedBy(if (status == VpnStatusTvViewState.Status.Connecting) 5.dp else 12.dp),
        modifier = modifier,
    ) {
        when (status) {
            VpnStatusTvViewState.Status.Unprotected ->
                StatusIcon(CoreR.drawable.ic_proton_lock_open_filled_2, textColor)
            VpnStatusTvViewState.Status.Connecting ->
                CircularProgressIndicator(
                    color = ProtonTheme.colors.iconNorm,
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .padding(2.dp)
                        .size(StatusIconSize - 4.dp)
                )
            VpnStatusTvViewState.Status.Protected ->
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
private fun LocationChipTv(
    location: VpnStatusTvViewState.Location,
    isMasking: Boolean,
    modifier: Modifier = Modifier,
) {
    val text = when {
        location.label != null -> vpnLocationText(location.label, location.ip)
        location.ip != null -> AnnotatedString(location.ip)
        else -> AnnotatedString(stringResource(R.string.stateFragmentUnknownIp))
    }
    val style = ProtonTheme.typography.body2Regular.copy(color = ProtonTheme.colors.textWeak)
    Box(
        modifier
            .background(ProtonTheme.colors.backgroundDeep, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        if (isMasking) {
            AnimateTextMasking(text, style)
        } else {
            Text(text, style = style)
        }
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
            VpnStatusTv(
                VpnStatusTvViewState(
                    VpnStatusTvViewState.Status.Unprotected,
                    VpnStatusTvViewState.Location("Norway", "123.127.237.252"),
                ),
                onActionClick = {},
            )
            VpnStatusTv(
                VpnStatusTvViewState(
                    VpnStatusTvViewState.Status.Unprotected,
                    VpnStatusTvViewState.Location(null, null),
                ),
                onActionClick = {},
            )
            VpnStatusTv(
                VpnStatusTvViewState(
                    VpnStatusTvViewState.Status.Protected,
                    VpnStatusTvViewState.Location("United States - Dallas", "154.47.24.201"),
                ),
                onActionClick = {},
            )
        }
    }
}
