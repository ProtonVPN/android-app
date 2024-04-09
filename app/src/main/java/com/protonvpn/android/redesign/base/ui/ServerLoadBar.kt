/*
 * Copyright (c) 2024 Proton AG
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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.protonvpn.android.base.ui.theme.LightAndDarkPreview
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun ServerLoadBar(progress: Float, modifier: Modifier = Modifier) {
    val trackColor = ProtonTheme.colors.shade40
    val loadColor = when {
        progress <= 0.75F -> ProtonTheme.colors.notificationSuccess
        progress <= 0.9F -> ProtonTheme.colors.notificationWarning
        else -> ProtonTheme.colors.notificationError
    }
    val thickness = 4.dp
    Canvas(modifier = modifier.size(36.dp, thickness)) {
        val lineY = size.height / 2
        val strokeCapThickness = thickness.toPx() / 2
        drawLine(
            trackColor,
            Offset(strokeCapThickness, lineY),
            Offset(size.width - strokeCapThickness, lineY),
            strokeWidth = thickness.toPx(),
            cap = StrokeCap.Round,
        )
        if (progress > 0f) {
            val progressWidth = progress * (size.width - 2 * strokeCapThickness)
            drawLine(
                loadColor,
                Offset(strokeCapThickness, lineY),
                Offset(strokeCapThickness + progressWidth, lineY),
                strokeWidth = thickness.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Preview
@Composable
private fun PreviewServerLoadBar() {
    LightAndDarkPreview {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0f, 0.01f,  0.76f, 1f).forEach {
                ServerLoadBar(it)
            }
        }
    }
}
