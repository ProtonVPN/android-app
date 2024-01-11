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

import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.protonvpn.android.redesign.base.ui.vpnGreen
import com.protonvpn.android.tv.main.CountryHighlight
import com.protonvpn.android.tv.main.CountryHighlightInfo
import com.protonvpn.android.tv.main.MapRendererConfig
import com.protonvpn.android.tv.main.TvMapRenderer
import com.protonvpn.android.tv.main.translateMapCoordinatesToRegion
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.relativePadding
import kotlinx.coroutines.CoroutineScope
import me.proton.core.compose.theme.ProtonTheme

private const val MAP_FADE_IN_DURATION = 250L

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
        border = ProtonTheme.colors.separatorNorm.toArgb(),
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
            val centerPointRegion = RectF(bounds.centerX(), bounds.centerY(), 0f, 0f)
                .translateMapCoordinatesToRegion()
            pins = listOf(PinInfo(centerPointRegion, highlight))
        }
    }
    focusRegionInCenter(scope, region, highlights, pins, bias = 0.4f, highlightStage = mapHighlight?.second)
}