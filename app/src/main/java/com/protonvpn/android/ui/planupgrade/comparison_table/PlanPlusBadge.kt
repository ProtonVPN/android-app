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

package com.protonvpn.android.ui.planupgrade.comparison_table

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.protonvpn.android.base.ui.vpnGreen
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun PlanPlusBadge(
    modifier: Modifier = Modifier,
) {
    val borderGradient = Brush.linearGradient(
        0f to ProtonTheme.colors.vpnGreen.copy(alpha = 0f),
        1f to ProtonTheme.colors.vpnGreen,
        start = Offset.Infinite.copy(x = 0f),
        end = Offset.Infinite.copy(y = 0f),
    )
    Text(
        "Plus",
        modifier = modifier
            .border(1.dp, borderGradient, ProtonTheme.shapes.medium)
            .background(
                ProtonTheme.colors.backgroundNorm,
                ProtonTheme.shapes.medium
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}