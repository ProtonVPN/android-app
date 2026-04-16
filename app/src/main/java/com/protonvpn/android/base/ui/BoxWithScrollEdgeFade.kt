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

package com.protonvpn.android.base.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.proton.core.compose.theme.ProtonTheme

private val FadeEnterAnimation = fadeIn(tween(750))
private val FadeExitAnimation = fadeOut(tween(750))

object BoxWithVerticalScrollEdgeFadeDefaults {
    val FadeHeight = 48.dp
}

/**
 * A box container for vertically scrolling content. The box adds a gradient at the top and bottom
 * to simulate edge fade for the scrolling content.
 * It's not a true fade as in "content becomes transparent". The gradients cover the content but if
 * they use color of the background this simulates fade well enough.
 *
 * Usage:
 *
 *    val scrollState = rememberScrollState() // Or rememberLazyListState().
 *    BoxWithVerticalScrollEdgeFade(
 *      scrollState = scrollState
 *    ) {
 *      Column(
 *        modifier = Modifier.verticalScroll(scrollState)
 *      ) {
 *        // Tall content
 *      }
 *    }
 */
@Composable
fun BoxWithVerticalScrollEdgeFade(
    scrollableState: ScrollableState,
    modifier: Modifier = Modifier,
    topFadeHeight: Dp = BoxWithVerticalScrollEdgeFadeDefaults.FadeHeight,
    bottomFadeHeight: Dp = BoxWithVerticalScrollEdgeFadeDefaults.FadeHeight,
    topFadeColor: Color = ProtonTheme.colors.backgroundNorm,
    bottomFadeColor: Color = ProtonTheme.colors.backgroundNorm,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier,
    ) {
        content()

        // Top fade:
        AnimatedVisibility(
            scrollableState.canScrollBackward,
            enter = FadeEnterAnimation,
            exit = FadeExitAnimation,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            val gradient = Brush.verticalGradient(
                0f to topFadeColor,
                1f to Color.Transparent,
            )
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topFadeHeight)
                    .background(gradient)
            )
        }
        // Bottom fade:
        AnimatedVisibility(
            scrollableState.canScrollForward,
            enter = FadeEnterAnimation,
            exit = FadeExitAnimation,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            val gradient = Brush.verticalGradient(
                0f to Color.Transparent,
                1f to bottomFadeColor
            )
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bottomFadeHeight)
                    .background(gradient)
            )
        }
    }
}