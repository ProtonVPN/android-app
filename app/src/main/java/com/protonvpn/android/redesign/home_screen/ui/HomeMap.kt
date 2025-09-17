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
package com.protonvpn.android.redesign.home_screen.ui

import android.graphics.PointF
import android.graphics.RectF
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import com.protonvpn.android.base.ui.vpnGreen
import com.protonvpn.android.tv.main.CountryHighlight
import com.protonvpn.android.tv.main.CountryHighlightInfo
import com.protonvpn.android.tv.main.MapRendererConfig
import com.protonvpn.android.tv.main.TvMapRenderer
import com.protonvpn.android.tv.main.translateMapCoordinatesToRegion
import com.protonvpn.android.tv.main.translateOldToNewMapCoordinates
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.relativePadding
import kotlinx.coroutines.CoroutineScope
import me.proton.core.compose.theme.ProtonTheme
import kotlin.math.roundToInt

private const val MAP_FADE_IN_DURATION = 250L
private val MapEasing = CubicBezierEasing(0f, 0f, 0.5f, 1f)

class MapParallaxAlignment(
    private val recentsExpandProgress: Float
) : Alignment {
    override fun align(size: IntSize, space: IntSize, layoutDirection: LayoutDirection): IntOffset {
        val maxExpand = 0.7f
        val displacementRatio = 0.05f
        val parallaxProgress = recentsExpandProgress.coerceAtMost(maxExpand) / maxExpand
        val offset = space.height * displacementRatio * MapEasing.transform(parallaxProgress)
        return IntOffset(0, -offset.roundToInt())
    }
}

@Composable
fun HomeMap(
    modifier: Modifier,
    scope: CoroutineScope,
    mapState: Pair<String, CountryHighlight>,
    elapsedRealtimeClock: () -> Long,
) {
    val mapConfig = MapRendererConfig(
        background = ProtonTheme.colors.backgroundNorm.toArgb(),
        country = ProtonTheme.colors.shade15.toArgb(),
        border = ProtonTheme.colors.shade50.toArgb(),
        selected = ProtonTheme.colors.shade40.toArgb(),
        connecting = ProtonTheme.colors.shade40.toArgb(),
        connected = ProtonTheme.colors.shade40.toArgb(),
        borderWidth = 3f,
        zoomIndependentBorderWidth = true
    )
    val pinColorConfig = mapOf(
        CountryHighlight.SELECTED to ProtonTheme.colors.notificationError.toArgb(),
        CountryHighlight.CONNECTING to ProtonTheme.colors.iconWeak.toArgb(),
        CountryHighlight.CONNECTED to ProtonTheme.colors.vpnGreen.toArgb(),
    )
    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).apply {
                init(mapConfig, pinColorConfig, MAP_FADE_IN_DURATION, elapsedRealtimeClock)
                update(scope, mapState)
            }
        },
        update = { map ->
            map.update(scope, mapState)
        }
    )
}

private fun MapView.update(
    scope: CoroutineScope,
    mapHighlight: Pair<String, CountryHighlight>?
) {
    var region = TvMapRenderer.DEFAULT_PORTRAIT_REGION
    var highlights = emptyList<CountryHighlightInfo>()
    var pins = emptyList<PinInfo>()
    mapHighlight?.let { (countryCode, highlight) ->
        val countryName = CountryTools.codeToMapCountryName[countryCode]
        val bounds = CountryTools.tvMapNameToBounds[countryName]
        if (bounds != null && countryName != null) {
            region = bounds
                .relativePadding(.1f) // Padding relative to country size
                .translateMapCoordinatesToRegion()
                .withPadding(0.015f) // Absolute padding (proportional to distance),
            // makes smaller countries have more padding than big ones
            highlights = listOf(CountryHighlightInfo(countryName, highlight))
            // Translate location from old map
            val translatedPinPosition = CountryTools.oldMapLocations[countryCode]?.let {
                PointF(it.x.toFloat(), it.y.toFloat()).translateOldToNewMapCoordinates()
            }
            // Use translated location if is in bounds (might not be the case for very small
            // countries like Malta), otherwise fallback to center of country bounds
            val pinPosition = if (translatedPinPosition != null && bounds.contains(translatedPinPosition))
                translatedPinPosition
            else
                RectF(bounds.centerX(), bounds.centerY(), 0f, 0f)
            pins = listOf(PinInfo(pinPosition.translateMapCoordinatesToRegion(), highlight))
        }
    }
    focusRegionInCenter(scope, region, highlights, pins, bias = 0.4f, highlightStage = mapHighlight?.second)
}
