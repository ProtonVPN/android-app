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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.theme.LightAndDarkPreview
import me.proton.core.compose.theme.ProtonTheme
import kotlin.math.roundToInt

private val ServerLoadColorLow @Composable get() = ProtonTheme.colors.notificationSuccess
private val ServerLoadColorMid @Composable get() = ProtonTheme.colors.notificationWarning
private val ServerLoadColorHigh @Composable get() = ProtonTheme.colors.notificationError

@Composable
fun ServerLoadBar(progress: Float, modifier: Modifier = Modifier) {
    val trackColor = ProtonTheme.colors.shade40
    val loadColor = when {
        progress <= 0.75F -> ServerLoadColorLow
        progress <= 0.9F -> ServerLoadColorMid
        else -> ServerLoadColorHigh
    }
    val thickness = 4.dp
    Canvas(modifier = modifier
        .size(36.dp, thickness)
        .rtlMirror()
    ) {
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

@Composable
fun ServerLoadInfoBar(modifier: Modifier = Modifier) {
    val labels: @Composable () -> Unit = @Composable {
        listOf(0, 75, 90, 100).forEach {
            Text(
                stringResource(id = R.string.serverLoad, it),
                style = ProtonTheme.typography.overlineMedium
            )
        }
    }
    val barThickness = 8.dp
    val barLabelTopPadding = 8.dp
    val colorLow = ServerLoadColorLow
    val colorMid = ServerLoadColorMid
    val colorHigh = ServerLoadColorHigh
    Layout(
        content = labels,
        modifier = modifier
            .clearAndSetSemantics { } // This is basically an illustration.
            .drawBehind {
                val lineCenter = barThickness.toPx() / 2
                val strokeCapPadding = barThickness.toPx() / 2
                val adjustedWidth = size.width - 2 * strokeCapPadding
                drawLine(
                    colorLow,
                    Offset(strokeCapPadding, lineCenter),
                    Offset(strokeCapPadding + adjustedWidth * 0.75f, lineCenter),
                    barThickness.toPx(),
                    StrokeCap.Round
                )
                drawLine(
                    colorHigh,
                    Offset(strokeCapPadding + adjustedWidth * 0.9f, lineCenter),
                    Offset(size.width - strokeCapPadding, lineCenter),
                    barThickness.toPx(),
                    StrokeCap.Round
                )
                // Draw over the rounded caps in the middle.
                drawLine(
                    colorMid,
                    Offset(strokeCapPadding + adjustedWidth * 0.75f, lineCenter),
                    Offset(strokeCapPadding + adjustedWidth * 0.9f, lineCenter),
                    barThickness.toPx(),
                    StrokeCap.Butt
                )
            },
    ) { measurables: List<Measurable>, constraints: Constraints ->
        val childConstraints = Constraints()
        val placeables = measurables.map {
                measurable -> measurable.measure(childConstraints)
        }
        val maxHeight = placeables.maxOf { it.height }
        val rowWidth = constraints.maxWidth
        val textY = (barThickness.toPx() + barLabelTopPadding.toPx()).roundToInt()
        val labelSpacing = 4.dp
        layout(rowWidth, maxHeight + textY) {
            placeables.first().placeRelative(0, textY)
            val lastLabelPosition = rowWidth - placeables.last().width
            placeables.last().placeRelative(lastLabelPosition, textY)

            with(placeables[1]) { placeRelative((rowWidth * 0.75 - this.width / 2).roundToInt(), textY) }

            with(placeables[2]) {
                val max90Position = (lastLabelPosition - labelSpacing.toPx() - this.width)
                placeRelative(minOf((rowWidth * 0.9f - this.width), max90Position).roundToInt(), textY)
            }
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
