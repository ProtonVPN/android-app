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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.protonvpn.android.redesign.base.ui.vpnGreen
import com.protonvpn.android.tv.main.CountryHighlight
import com.protonvpn.android.tv.main.CountryHighlightInfo
import com.protonvpn.android.tv.main.MapRendererConfig
import com.protonvpn.android.tv.main.TvMapRenderer
import com.protonvpn.android.tv.main.TvMapView
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
    mapState: Pair<String, CountryHighlight>
) {
    val mapConfig = MapRendererConfig(
        background = ProtonTheme.colors.backgroundNorm.toArgb(),
        country = ProtonTheme.colors.shade15.toArgb(),
        border = ProtonTheme.colors.separatorNorm.toArgb(),
        selected = ProtonTheme.colors.shade40.toArgb(),
        connecting = ProtonTheme.colors.shade100.copy(alpha = 0.5f).toArgb(),
        connected = ProtonTheme.colors.vpnGreen.copy(alpha = 0.5f).toArgb(),
        borderWidth = 3f,
        zoomIndependentBorderWidth = true
    )
    AndroidView(
        modifier = modifier,
        factory = { context ->
            TvMapView(context).apply {
                init(mapConfig, fadeInDurationMs = MAP_FADE_IN_DURATION)
                update(scope, mapState)
            }
        },
        update = { map ->
            map.update(scope, mapState)
        }
    )
}

private fun TvMapView.update(
    scope: CoroutineScope,
    mapHighlight: Pair<String, CountryHighlight>?
) {
    var region = TvMapRenderer.DEFAULT_PORTRAIT_REGION
    var highlights = emptyList<CountryHighlightInfo>()
    if (mapHighlight != null) {
        val countryName = CountryTools.codeToMapCountryName[mapHighlight.first]
        val bounds = CountryTools.tvMapNameToBounds[countryName]
        if (bounds != null && countryName != null) {
            region = bounds
                .relativePadding(.1f) // Padding relative to country size
                .translateMapCoordinatesToRegion()
                .withPadding(0.015f) // Absolute padding (proportional to distance),
            // makes smaller countries have more padding than big ones
            highlights = listOf(CountryHighlightInfo(countryName, mapHighlight.second))
        }
    }
    focusRegionInCenter(scope, region, highlights, bias = 0.4f)
}