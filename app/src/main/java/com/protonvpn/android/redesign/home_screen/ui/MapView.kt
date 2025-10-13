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

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.provider.Settings
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.core.graphics.and
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.BuildConfig
import com.protonvpn.android.R
import com.protonvpn.android.tv.main.CountryHighlight
import com.protonvpn.android.tv.main.CountryHighlightInfo
import com.protonvpn.android.tv.main.MapRegion
import com.protonvpn.android.tv.main.MapRendererConfig
import com.protonvpn.android.tv.main.RenderedMap
import com.protonvpn.android.tv.main.TvMapRenderer
import com.protonvpn.android.tv.main.translateNewToOldMapCoordinates
import com.protonvpn.android.tv.main.translateRegionPointToMapCoordinates
import com.protonvpn.android.utils.ViewUtils.toPx
import com.protonvpn.android.utils.inCoordsOf
import com.protonvpn.android.utils.scale
import com.protonvpn.android.utils.withPadding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val FUZZY_BORDER_COUNTRIES = setOf("India")

data class PinInfo(val pos: MapRegion, val highlight: CountryHighlight)

class MapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // When and what highlight stage started.
    private var renderTimeInfo : Pair<Long, CountryHighlight?>? = null
    private var currentRenderData : RenderData? = null
    private var targetRenderData : RenderData? = null
    private var renderedMap: RenderedMap? = null

    data class RenderData(
        val region: MapRegion,
        val pins: List<PinInfo>,
        val stage: CountryHighlight?,
        val id: Long // id will link rendered map to region and pins
    )

    // Pre-allocated structs to avoid allocations in onDraw
    private var viewRect = RectF(0f, 0f, 1f, 1f)
    private val pinInterpolator = DecelerateInterpolator(1.5f)

    private val outerPinBitmapDisconnected = BitmapFactory.decodeResource(resources, R.drawable.map_pin_outer_disconnected)
    private val outerPinBitmapProtected = BitmapFactory.decodeResource(resources, R.drawable.map_pin_outer_protected)
    private val innerPinPaintOutside = Paint().apply { color = Color.WHITE }

    private lateinit var mapRenderer: TvMapRenderer
    private lateinit var elapsedClockMs: () -> Long
    private lateinit var pinColorPaints: Map<CountryHighlight, Paint>
    private var animate: Boolean = true

    fun init(
        config: MapRendererConfig,
        pinColorConfig: Map<CountryHighlight, Int>,
        fadeInDurationMs: Long,
        elapsedClockMs: () -> Long
    ) {
        this.elapsedClockMs = elapsedClockMs
        this.pinColorPaints = pinColorConfig.mapValues { Paint().apply { color = it.value } }
        alpha = 0f
        animate = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) != 0f
        mapRenderer = TvMapRenderer(
            context,
            (context as ComponentActivity).lifecycleScope,
            config,
            FUZZY_BORDER_COUNTRIES
        ) { map, id ->
            targetRenderData?.let { renderData ->
                if (id == renderData.id) {
                    if (renderedMap == null) {
                        animate()
                            .alpha(1f)
                            .duration = fadeInDurationMs
                    }
                    renderedMap = map
                    renderTimeInfo = Pair(elapsedClockMs(), renderData.stage)
                    currentRenderData = targetRenderData

                    invalidate()
                }
            }
        }
        if (BuildConfig.DEBUG) setOnTouchListener { view, event ->
            val region = renderedMap?.region
            if (event.action == MotionEvent.ACTION_DOWN && region != null)
                logMapLocation(region, event.x, event.y)
            false
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0)
            return

        val (region, pins) = currentRenderData ?: return
        renderedMap?.let { renderedMap ->
            if (region.w == 0f || renderedMap.region.isEmpty)
                return

            val regionRect = region.toRectF()
            val src = regionRect
                .and(renderedMap.region)
                .inCoordsOf(renderedMap.region)
                .scale(renderedMap.bitmap.width.toFloat(), renderedMap.bitmap.height.toFloat())

            val dstHeight = width.toFloat() * src.height() / src.width()
            val dstTop = (height - dstHeight) / 2
            viewRect.set(0f, dstTop, width.toFloat(), dstTop + dstHeight)
            canvas.drawBitmap(renderedMap.bitmap, Rect().apply { src.round(this) }, viewRect, null)

            canvas.drawPins(regionRect, pins)
        }
    }

    private fun innerPinRadius(elapsedS: Float, stage: CountryHighlight?) : Float? {
        if (!animate || stage == CountryHighlight.CONNECTED) return INNER_PIN_SIZE

        if (elapsedS < INNER_PIN_START_DELAY_S) return null
        val stageS = elapsedS - INNER_PIN_START_DELAY_S
        return if (stageS < INNER_PIN_SHOW_DURATION_S)
            INNER_PIN_SIZE * pinInterpolator.getInterpolation(stageS / INNER_PIN_SHOW_DURATION_S)
        else
            INNER_PIN_SIZE
    }

    private fun outerPinRadius(elapsedS: Float, stage: CountryHighlight?) : Float? {
        if (!animate) return OUTER_PIN_FULL_SIZE

        val startDelay = if (stage == CountryHighlight.CONNECTED) 0f else OUTER_PIN_START_DELAY_S
        if (elapsedS < startDelay) return null
        val stageS = elapsedS - startDelay
        return if (stageS < OUTER_PIN_SHOW_DURATION_S) {
            // Showing
            OUTER_PIN_FULL_SIZE * pinInterpolator.getInterpolation(stageS / OUTER_PIN_SHOW_DURATION_S)
        } else {
            // Pulsing
            val pulseStageS = (stageS - OUTER_PIN_SHOW_DURATION_S) % OUTER_PIN_PULSE_DURATION_S
            val halfPulse = OUTER_PIN_PULSE_DURATION_S / 2
            val size = if (pulseStageS > halfPulse) {
                // Growing phase
                pinInterpolator.getInterpolation((pulseStageS - halfPulse) / halfPulse)
            } else {
                // Shrinking phase
                1f - pinInterpolator.getInterpolation(pulseStageS / halfPulse)
            }
            val diff = OUTER_PIN_FULL_SIZE - OUTER_PIN_SMALL_SIZE
            OUTER_PIN_SMALL_SIZE + diff * size
        }
    }

    private fun Canvas.drawPins(regionRect: RectF, pins: List<PinInfo>) {
        renderTimeInfo?.let { timeInfo ->
            val animationElapsedS = (elapsedClockMs() - timeInfo.first) / 1000f

            for (pin in pins) {
                val pinInViewCoord = pin.pos.toRectF()
                    .inCoordsOf(regionRect)
                    .scale(width.toFloat(), height.toFloat())

                val outerPinBitmap = when (pin.highlight) {
                    CountryHighlight.SELECTED -> outerPinBitmapDisconnected
                    CountryHighlight.CONNECTED -> outerPinBitmapProtected
                    CountryHighlight.CONNECTING -> null // No outer bitmap when connecting
                }
                if (outerPinBitmap != null) {
                    outerPinRadius(animationElapsedS, timeInfo.second)?.let { padding ->
                        drawBitmap(
                            outerPinBitmap,
                            null,
                            pinInViewCoord.withPadding(padding),
                            null
                        )
                    }
                }
                innerPinRadius(animationElapsedS, timeInfo.second)?.let { radius ->
                    pinColorPaints[pin.highlight]?.let { innerPaint ->
                        drawCircle(pinInViewCoord.left, pinInViewCoord.top, radius, innerPinPaintOutside)
                        drawCircle(pinInViewCoord.left, pinInViewCoord.top, radius / 2, innerPaint)
                    }
                }
            }
            // Keep animating pins
            if (pins.isNotEmpty() && animate)
                invalidate()
        }
    }

    override fun layout(l: Int, t: Int, r: Int, b: Int) {
        super.layout(l, t, r, b)
        var w = r - l
        var h = b - t

        if (w > 0 && h > 0) {
            // Limit bitmap size to avoid OOM on devices with very high res
            if (w * h > BITMAP_MAX_PIXELS) {
                val normalH = h.toFloat() / w
                w = sqrt(BITMAP_MAX_PIXELS / normalH).roundToInt()
                h = (w * normalH).roundToInt()
            }
            val newId = mapRenderer.updateSize(w, h)
            if (newId != null)
                targetRenderData = targetRenderData?.copy(id = newId)
        }
    }

    // Crops to show given region without animation putts it in the center of the viewport
    // (with given bias). Will not keep resulting region in map bounds (will add padding to keep
    // focused region in the center).
    fun focusRegionInCenter(
        mainScope: CoroutineScope,
        focusRegion: MapRegion,
        newHighlights: List<CountryHighlightInfo>?,
        newPins: List<PinInfo>,
        highlightStage: CountryHighlight?,
        bias: Float,
    ) = mainScope.launch {
        val viewportNormalH = height / width.toFloat()
        val newRegion = focusRegion.expandToAspectRatio(viewportNormalH, bias)
        val id = mapRenderer.update(
            newMapRegion = newRegion,
            newHighlights = newHighlights,
        )
        targetRenderData = RenderData(newRegion, newPins, highlightStage, id)
    }

    companion object {
        const val BITMAP_MAX_PIXELS = 5_000_000 // ~2.7k

        const val INNER_PIN_START_DELAY_S = 0.2f
        const val INNER_PIN_SHOW_DURATION_S = 0.4f

        const val OUTER_PIN_START_DELAY_S = 0.7f
        const val OUTER_PIN_SHOW_DURATION_S = 1f
        const val OUTER_PIN_PULSE_DURATION_S = 3f

        val OUTER_PIN_FULL_SIZE = 48.toPx().toFloat()
        val OUTER_PIN_SMALL_SIZE = 32.toPx().toFloat()
        val INNER_PIN_SIZE = 12.toPx().toFloat()
    }
}

fun logMapLocation(region: RectF, x: Float, y: Float) {
    val px = region.left + x * region.width()
    val py = region.top + y * region.height()
    val newMapCoord = PointF(px, py).translateRegionPointToMapCoordinates()
    val oldMapCoord = newMapCoord.translateNewToOldMapCoordinates()
    println("Map coordinates: new=$newMapCoord old=$oldMapCoord")
}
