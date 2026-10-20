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

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.protonvpn.android.base.ui.vpnGreen
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun VpnStatusBackgroundTv(
    state: VpnStatusTvViewState.Status?,
    modifier: Modifier = Modifier,
) {
    val targetColor = when (state) {
        VpnStatusTvViewState.Status.Unprotected -> ProtonTheme.colors.notificationError
        VpnStatusTvViewState.Status.Connecting -> ProtonTheme.colors.notificationNorm
        VpnStatusTvViewState.Status.Protected -> ProtonTheme.colors.vpnGreen.copy(alpha = 0.5f) // TODO: add theming, use the new color.
        null -> Color.Transparent
    }
    val color by animateColorAsState(targetColor, animationSpec = tween(500), label = "status background color")
    Spacer(
        // TODO: replace this AI generated monstrosity with gradient definition from
        //  designers.
        modifier = modifier
            .aspectRatio(1920f / 868f)
            .drawWithCache {
                val a = color.alpha
                val vertical = Brush.verticalGradient(
                    0.00f to color.copy(alpha = a * 0.50f),
                    0.10f to color.copy(alpha = a * 0.47f),
                    0.20f to color.copy(alpha = a * 0.33f),
                    0.30f to color.copy(alpha = a * 0.20f),
                    0.40f to color.copy(alpha = a * 0.095f),
                    0.50f to color.copy(alpha = a * 0.035f),
                    0.60f to color.copy(alpha = a * 0.008f),
                    0.75f to Color.Transparent,
                )
                val edgeRadius = size.width * 0.35f
                val edgeY = size.height * 0.42f
                fun edge(x: Float) = Brush.radialGradient(
                    0f to color.copy(alpha = a * 0.09f),
                    1f to Color.Transparent,
                    center = Offset(x, edgeY),
                    radius = edgeRadius,
                )
                val left = edge(0f)
                val right = edge(size.width)
                onDrawBehind {
                    drawRect(vertical)
                    drawRect(left)
                    drawRect(right)
                }
            }
    )
}
