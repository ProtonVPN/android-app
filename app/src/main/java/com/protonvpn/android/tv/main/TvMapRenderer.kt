/*
 * Copyright (c) 2020 Proton AG
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
import android.graphics.PointF
import android.graphics.RectF
import androidx.annotation.ColorInt
import com.caverock.androidsvg.PreserveAspectRatio
import com.caverock.androidsvg.RenderOptions
import com.caverock.androidsvg.SVG
import com.protonvpn.android.utils.scale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import okhttp3.internal.toHexString

data class RenderedMap(val bitmap: Bitmap, val region: RectF)

enum class CountryHighlight {
    SELECTED,
    CONNECTING,
    CONNECTED
}
data class CountryHighlightInfo(val country: String, val highlight: CountryHighlight)

data class MapRendererConfig(
    @ColorInt val background: Int,
    @ColorInt val country: Int,
    @ColorInt val border: Int,
    @ColorInt val selected: Int,
    @ColorInt val connecting: Int,
    @ColorInt val connected: Int,
    val borderWidth: Float,
    val zoomIndependentBorderWidth: Boolean,
)

class TvMapRenderer(
    context: Context,
    private val scope: CoroutineScope,
    private val config: MapRendererConfig,
    private val fuzzyBorderCountries: Set<String>,
    private val bitmapCallback: (RenderedMap, Long) -> Unit
) {
    class RenderTarget(w: Int, h: Int) {
        val map: Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas: Canvas = Canvas(map)
        val outMap: Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val outCanvas: Canvas = Canvas(outMap)

        fun isSize(w: Int, h: Int) = map.width == w && map.height == h
    }

    private val renderContext = newSingleThreadContext("tv.map_renderer")
    private var currentId = 0L // ID relating render requests with their results

    private val mapSvg: Deferred<SVG> = scope.async(renderContext) {
        SVG.getFromAsset(context.resources.assets, ASSET_NAME)
    }

    private var renderTarget: RenderTarget? = null

    private var highlights = listOf<CountryHighlightInfo>()
    private var mapRegion : MapRegion? = null

    private var renderJob: Job? = null

    fun updateSize(w: Int, h: Int) : Long? {
        if (renderTarget?.isSize(w, h) != true) {
            val id = ++currentId
            renderTarget = RenderTarget(w, h)
            scope.launch {
                renderTarget?.render(id)
            }
            return id
        }
        return null
    }

    private val CountryHighlight.cssColor get() = when(this) {
        CountryHighlight.SELECTED -> toCssColor(config.selected)
        CountryHighlight.CONNECTING -> toCssColor(config.connecting)
        CountryHighlight.CONNECTED -> toCssColor(config.connected)
    }

    private suspend fun RenderTarget.render(id: Long) {
        val regionToRender = mapRegion ?: return

        renderJob?.cancelAndJoin()
        renderJob = scope.launch(renderContext) {
            val highlightsCss = highlights
                .filter { it.country !in fuzzyBorderCountries }
                .joinToString(separator = " ") { (country, highlight) ->
                    "#$country { fill: ${highlight.cssColor}; }"
                }
            val borderWidth = if (config.zoomIndependentBorderWidth) {
                config.borderWidth * regionToRender.w // Borders will be the same regardless of zoom level
            } else {
                config.borderWidth
            }
            val noBorder = highlights.any { it.country in fuzzyBorderCountries }
            val borderColor = if (noBorder) config.country else config.border
            val css = "$highlightsCss path { fill: ${toCssColor(config.country)}; stroke: ${toCssColor(borderColor)}; stroke-width: $borderWidth; }"
            val width = map.width.toFloat()
            val height = map.height.toFloat()

            val svg = mapSvg.await()
            val documentWidth = svg.documentWidth
            val documentHeight = svg.documentHeight
            val viewportNormalH = height / width
            val region = regionToRender.expandToAspectRatio(viewportNormalH)
            val viewBoxScale = documentWidth / width
            val regionScale = region.w
            val renderOptions = RenderOptions()
                    .css(css)
                    .preserveAspectRatio(PreserveAspectRatio.STRETCH)
                    .viewBox(region.x * documentWidth,
                            region.y * documentWidth,
                            documentWidth * viewBoxScale * regionScale,
                            documentHeight * viewBoxScale * regionScale)

            canvas.drawColor(config.background)
            svg.renderToCanvas(canvas, renderOptions)

            // If current render job was canceled don't produce and pass output bitmap to client,
            // but if it's still active don't suspend and finish blocking current (background)
            // thread to avoid starting new render before map is fully copied to output bitmap.
            if (isActive) runBlocking(Dispatchers.Main) {
                outCanvas.drawBitmap(map, 0f, 0f, null)
                val regionHeight = height / width * region.w
                val regionRect = RectF(region.x, region.y, region.x + region.w, region.y + regionHeight)
                bitmapCallback(RenderedMap(outMap, regionRect), id)
            }
        }
        renderJob?.join()
    }

    fun update(
        newHighlights: List<CountryHighlightInfo>? = null,
        newMapRegion: MapRegion? = null,
    ) : Long {
        val regionChanged = newMapRegion != null && newMapRegion != mapRegion
        val highlightsChanged = newHighlights != null && newHighlights != highlights
        if (highlightsChanged || regionChanged) {
            if (newMapRegion != null)
                mapRegion = newMapRegion
            if (newHighlights != null)
                highlights = newHighlights
            val id = ++currentId
            scope.launch {
                renderTarget?.render(id)
            }
            return id
        }
        return currentId
    }

    companion object {
        // Source: https://simplemaps.com/
        // License: https://simplemaps.com/resources/svg-license
        private const val ASSET_NAME = "world.svg"

        const val WIDTH = 1538.434f
        const val HEIGHT = 700f
        const val NORMAL_H = HEIGHT / WIDTH

        val FULL_REGION = MapRegion(0f, 0f, 1f, NORMAL_H)
        val DEFAULT_PORTRAIT_REGION = MapRegion(.15f, 0f, .60f, NORMAL_H)

        private fun toCssColor(@ColorInt argb: Int): String {
            val rgba = 0xFF000000.toInt() and argb ushr 24 or (argb shl 8)
            return "#${rgba.toHexString().padStart(8, '0')}"
        }
    }
}

fun RectF.translateMapCoordinatesToRegion() =
    scale(1f / TvMapRenderer.WIDTH, 1f / TvMapRenderer.WIDTH).run {
        MapRegion(left, top, width(), height())
    }

fun PointF.translateRegionPointToMapCoordinates() =
    PointF(x *TvMapRenderer.WIDTH, y * TvMapRenderer.WIDTH)

fun PointF.translateNewToOldMapCoordinates() : PointF {
    val oldX = (x - 60.36402f) / 0.28127f
    val oldY = (y + 2.25f) / 0.28258f
    return PointF(oldX, oldY)
}

// Translates point on the old map to a new one with linear regression (seems to work well enough)
fun PointF.translateOldToNewMapCoordinates() : RectF {
    val newX = 0.28127f * x + 60.36402f
    val newY = 0.28258f * y - 2.25f
    return RectF(newX, newY, newX, newY)
}