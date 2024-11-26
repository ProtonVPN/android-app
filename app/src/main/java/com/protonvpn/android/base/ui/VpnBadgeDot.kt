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

package com.protonvpn.android.base.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun BoxScope.VpnBadgeDot(
    modifier: Modifier = Modifier,
    size: Dp = 7.dp,
    color: Color = ProtonTheme.colors.notificationError,
    borderWidth: Dp = 2.dp,
    borderColor: Color = ProtonTheme.colors.backgroundNorm,
) {
    Spacer(
        modifier = modifier
            .offset(x = borderWidth, y = -borderWidth)
            .size(size + 2 * borderWidth)
            // Don't use border, it produces artifacts.
            .background(borderColor, CircleShape)
            .padding(borderWidth)
            .background(color, CircleShape)
            .align(Alignment.TopEnd)
    )
}
