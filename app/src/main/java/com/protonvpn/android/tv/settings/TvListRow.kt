/*
 * Copyright (c) 2024. Proton AG
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

package com.protonvpn.android.tv.settings

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import com.protonvpn.android.redesign.base.ui.optional
import com.protonvpn.android.tv.ui.TvUiConstants
import com.protonvpn.android.tv.ui.onFocusLost
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.isNightMode
import me.proton.core.presentation.compose.tv.theme.ProtonThemeTv

@Composable
fun ProtonTvFocusableSurface(
    onClick: () -> Unit,
    focusedColor: @Composable () -> Color,
    shape: Shape,
    modifier: Modifier = Modifier,
    color: @Composable () -> Color = { Color.Transparent },
    clickSound: Boolean = true,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    ProtonThemeTv(
        isDark = if (focused) !isNightMode() else isNightMode()
    ) {
        // Remove when implemented in Compose TV: https://issuetracker.google.com/issues/268268856
        // (can't use the suggestion from issuetracker with `clickable` modifier because it breaks the `Surface`
        // selection).
        val context = LocalContext.current
        val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

        Surface(
            onClick = {
                if (clickSound) audioManager.playSoundEffect(AudioManager.FX_KEY_CLICK)
                onClick()
            },
            colors = ClickableSurfaceDefaults.colors(
                containerColor = color(),
                contentColor = LocalContentColor.current,
                focusedContainerColor = focusedColor(),
                focusedContentColor = LocalContentColor.current,
            ),
            shape = ClickableSurfaceDefaults.shape(shape),
            scale = ClickableSurfaceScale.None,
            interactionSource = interactionSource,
            modifier = modifier
                .onFocusLost { audioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_UP) }
        ) {
            content()
        }
    }
}

@Composable
fun ProtonTvFocusableSurface(
    modifier: Modifier = Modifier,
    focusedColor: @Composable () -> Color = { ProtonTheme.colors.textHint },
    shape: Shape = ProtonTheme.shapes.large,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }

    val isFocused by interactionSource.collectIsFocusedAsState()

    val context = LocalContext.current

    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    Surface(
        modifier = modifier
            .optional(
                predicate = { isFocused },
                modifier = Modifier.border(
                    width = 2.dp,
                    color = focusedColor(),
                    shape = shape,
                )
            )
            .onFocusLost {
                audioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_UP)
            },
        onClick = {},
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = LocalContentColor.current,
            focusedContainerColor = Color.Transparent,
            focusedContentColor = LocalContentColor.current,
        ),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceScale.None,
        interactionSource = interactionSource,
    ) {
        content()
    }
}

@Composable
fun TvListRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    verticalContentPadding: Dp = 16.dp,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    clickSound: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    ProtonTvFocusableSurface(
        onClick = onClick,
        shape = ProtonTheme.shapes.large,
        focusedColor = { ProtonTheme.colors.backgroundNorm },
        clickSound = clickSound,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = verticalAlignment,
            horizontalArrangement = horizontalArrangement,
            modifier = Modifier
                .padding(horizontal = TvUiConstants.SelectionPaddingHorizontal,  vertical = verticalContentPadding),
            content = content
        )
    }
}
