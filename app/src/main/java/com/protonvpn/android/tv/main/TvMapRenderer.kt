/*
 * Copyright (c) 2020 Proton Technologies AG
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
package com.protonvpn.android.tv.main

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.caverock.androidsvg.PreserveAspectRatio
import com.caverock.androidsvg.RenderOptions
import com.caverock.androidsvg.SVG
import com.protonvpn.android.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import okhttp3.internal.toHexString

data class RenderedMap(val bitmap: Bitmap, val region: RectF)

class TvMapRenderer(
    context: Context,
    val scope: CoroutineScope,
    val bitmapCallback: (RenderedMap) -> Unit
) {
    class RenderTarget(w: Int, h: Int) {
        val map: Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas: Canvas = Canvas(map)
        val outMap: Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val outCanvas: Canvas = Canvas(outMap)

        fun isSize(w: Int, h: Int) = map.width == w && map.height == h
    }

    // Map region defined in coordinates where width is fixed as 1 (height will be calculated based
    // on aspect ratio of a target view port e.g. h=1 for square viewport)
    data class MapRegion(var x: Float, var y: Float, var w: Float) {
        fun toRectF(height: Float) =
                RectF(x, y, x + w, y + height * w)

        // Shifts current region so it fits in [0,0,1,height] rect, if possible (not too big).
        fun shiftedToBoundaries(height: Float) = copy().apply {
            if (y + w * height > height)
                y = height * (1 - w)
            if (x + w > 1f)
                x = 1f - w
            if (x < 0f)
                x = 0f
            if (y < 0f)
                y = 0f
            return this
        }
    }

    private val countryColor = context.getHexColor(R.color.tvMapCountry)
    private val selectedColor = context.getHexColor(R.color.tvMapSelected)
    private val connectedColor = context.getHexColor(R.color.tvMapConnected)
    private val borderColor = context.getHexColor(R.color.tvMapBorder)

    @OptIn(ObsoleteCoroutinesApi::class)
    private val renderContext = newSingleThreadContext("tv.map_renderer")

    private val mapSvg: Deferred<SVG> = scope.async(renderContext) {
        SVG.getFromAsset(context.resources.assets, ASSET_NAME)
    }

    private var renderTarget: RenderTarget? = null

    private var selectedId: String? = null
    private var connectedId: String? = null
    private var mapRegion = MapRegion(0f, 0f, 1f)

    private var renderJob: Job? = null

    suspend fun updateMapRegion(newMapRegion: MapRegion) {
        if (newMapRegion != mapRegion) {
            mapRegion = newMapRegion
            renderTarget?.render()
        }
    }

    fun updateSize(w: Int, h: Int) {
        if (renderTarget?.isSize(w, h) != true) {
            renderTarget = RenderTarget(w, h)
            scope.launch {
                renderTarget?.render()
            }
        }
    }

    private suspend fun RenderTarget.render() {
        renderJob?.cancelAndJoin()
        renderJob = scope.launch(renderContext) {
            val selected = selectedId
            val connected = connectedId

            val selectedCountryCss = if (selected != null && selected != connected)
                "#$selected { fill: $selectedColor; }" else ""
            val connectedCountryCss = if (connected != null)
                "#$connected { fill: $connectedColor; }" else ""
            val css = "$selectedCountryCss $connectedCountryCss " +
                "path { fill: $countryColor; stroke: $borderColor; stroke-width: 0.1; }"
            val width = map.width.toFloat()
            val height = map.height.toFloat()

            val svg = mapSvg.await()
            val documentWidth = svg.documentWidth
            val documentHeight = svg.documentHeight
            val region = mapRegion.shiftedToBoundaries(height / width)
            val viewBoxScale = documentWidth / width
            val regionScale = region.w
            val renderOptions = RenderOptions()
                    .css(css)
                    .preserveAspectRatio(PreserveAspectRatio.STRETCH)
                    .viewBox(region.x * documentWidth,
                            region.y * documentWidth,
                            documentWidth * viewBoxScale * regionScale,
                            documentHeight * viewBoxScale * regionScale)

            svg.renderToCanvas(canvas, renderOptions)

            // If current render job was canceled don't produce and pass output bitmap to client,
            // but if it's still active don't suspend and finish blocking current (background)
            // thread to avoid starting new render before map is fully copied to output bitmap.
            if (isActive) runBlocking(Dispatchers.Main) {
                outCanvas.drawBitmap(map, 0f, 0f, null)
                val regionHeight = height / width * region.w
                val regionRect = RectF(region.x, region.y, region.x + region.w, region.y + regionHeight)
                bitmapCallback(RenderedMap(outMap, regionRect))
            }
        }
        renderJob?.join()
    }

    fun updateSelection(selected: String?, connected: String?) {
        if (selected != selectedId || connected != connectedId) {
            selectedId = selected
            connectedId = connected
            scope.launch {
                renderTarget?.render()
            }
        }
    }

    companion object {
        // Source: https://simplemaps.com/
        // License: https://simplemaps.com/resources/svg-license
        private const val ASSET_NAME = "world.svg"

        const val WIDTH = 1538.434f

        private fun Context.getHexColor(@ColorRes res: Int): String {
            val argb = ContextCompat.getColor(this, res)
            val rgba = 0xFF000000.toInt() and argb ushr 24 or (argb shl 8)
            return "#${rgba.toHexString().padStart(8, '0')}"
        }
    }
}
