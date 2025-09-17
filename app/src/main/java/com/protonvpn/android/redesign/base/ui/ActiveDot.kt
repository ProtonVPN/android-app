/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.android.redesign.base.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.base.ui.vpnGreen
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun ActiveDot(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(IntrinsicSize.Min)
            .height(IntrinsicSize.Min)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_active_dot),
            contentDescription = stringResource(R.string.accessibility_item_connected),
            modifier = Modifier.fillMaxSize(),
            tint = ProtonTheme.colors.vpnGreen
        )
        PingAnimation(color = ProtonTheme.colors.vpnGreen, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun PingAnimation(
    color: Color,
    modifier: Modifier
) {
    val infiniteTransition = rememberInfiniteTransition("Active Dot")
    val animationSpec = infiniteRepeatable<Float>(
        animation = tween(1200, 2000),
        repeatMode = RepeatMode.Restart
    )
    val scale by infiniteTransition
        .animateFloat(initialValue = 0.4f, targetValue = 1f, animationSpec, "Scale")
    val alpha by infiniteTransition
        .animateFloat(initialValue = 0.8f, targetValue = 0f, animationSpec, "Alpha")
    Canvas(
        modifier = modifier
            .graphicsLayer {
                this.alpha = alpha
                this.scaleX = scale
                this.scaleY = scale
            },
        onDraw = {
            this.drawCircle(color)
        }
    )
}

@ProtonVpnPreview
@Composable
private fun ActiveDotPreview() {
    ProtonVpnPreview {
        ActiveDot()
    }
}
