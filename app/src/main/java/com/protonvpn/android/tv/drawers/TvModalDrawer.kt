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

package com.protonvpn.android.tv.drawers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun TvModalDrawer(
    isDrawerOpen: Boolean,
    drawerContent: @Composable ColumnScope.() -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    scrimColor: Color = Color.Black,
    drawerWidth: Dp = 300.dp,
) {
    val density = LocalDensity.current

    val animatedScrimColor by animateColorAsState(
        targetValue = if (isDrawerOpen) scrimColor.copy(alpha = 0.6f) else Color.Transparent,
        animationSpec = tween(),
    )

    Box(
        modifier = modifier
    ) {
        content()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color = animatedScrimColor)
        )

        AnimatedVisibility(
            visible = isDrawerOpen,
            enter = slideInHorizontally {
                with(density) { drawerWidth.roundToPx() }
            },
            exit = slideOutHorizontally {
                with(density) { drawerWidth.roundToPx() }
            },
            modifier = Modifier
                .align(alignment = Alignment.CenterEnd)
                .width(width = drawerWidth)
                .fillMaxHeight()

        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = 16.dp,
                        end = 32.dp,
                        bottom = 16.dp,
                    )
                    .clip(shape = ProtonTheme.shapes.large)
                    .background(color = ProtonTheme.colors.backgroundSecondary),
            ) {
                drawerContent()
            }
        }
    }
}
