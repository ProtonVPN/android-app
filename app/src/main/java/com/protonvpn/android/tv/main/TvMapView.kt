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

import android.animation.FloatEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.activity.ComponentActivity
import androidx.core.animation.addListener
import androidx.core.graphics.and
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.utils.inCoordsOf
import com.protonvpn.android.utils.scale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.roundToInt
import kotlin.math.sqrt

class TvMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var renderedMap: RenderedMap? = null
    private var region = TvMapRenderer.FULL_REGION
    private var targetRegion = TvMapRenderer.FULL_REGION
    private var viewRect = RectF(0f, 0f, 1f, 1f)

    private var currentZoomAnimation: Job? = null
    private var currentAnimator: ValueAnimator? = null

    private lateinit var mapRenderer: TvMapRenderer

    fun init(
        config: MapRendererConfig,
        showDelayMs: Long = 0,
        fadeInDurationMs: Long = 0,
    ) {
        alpha = 0f
        mapRenderer = TvMapRenderer(
            context,
            findViewTreeLifecycleOwner()!!.lifecycleScope,
            config,
            emptySet()
        ) { map, id ->
            if (renderedMap == null) {
                animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(showDelayMs)
                    .duration = fadeInDurationMs
            }
            renderedMap = map
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0)
            return

        renderedMap?.let { srcMap ->
            if (region.w == 0f || srcMap.region.isEmpty)
                return

            val regionRect = region.toRectF()
            val src = regionRect
                .and(srcMap.region)
                .inCoordsOf(srcMap.region)
                .scale(srcMap.bitmap.width.toFloat(), srcMap.bitmap.height.toFloat())

            val dstHeight = width.toFloat() * src.height() / src.width()
            val dstTop = (height - dstHeight) / 2
            viewRect.set(0f, dstTop, width.toFloat(), dstTop + dstHeight)
            canvas.drawBitmap(srcMap.bitmap, Rect().apply { src.round(this) }, viewRect, null)
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
            mapRenderer.updateSize(w, h)
        }
    }

    private suspend fun cancelAnimation() {
        currentAnimator?.cancel()
        currentZoomAnimation?.cancel()
        currentZoomAnimation?.join()
        currentAnimator = null
        currentZoomAnimation = null
    }

    // Crops to show given region without animation putts it in the center of the viewport
    // (with given bias). Will not keep resulting region in map bounds (will add padding to keep
    // focused region in the center).
    fun focusRegionInCenter(
        mainScope: CoroutineScope,
        focusRegion: MapRegion,
        newHighlights: List<CountryHighlightInfo>?,
        bias: Float,
    ) = mainScope.launch {
        cancelAnimation()
        val viewportNormalH = height / width.toFloat()
        val newRegion = focusRegion.expandToAspectRatio(viewportNormalH, bias)
        targetRegion = newRegion
        region = targetRegion
        mapRenderer.update(
            newMapRegion = targetRegion,
            newHighlights = newHighlights
        )
    }

    // Zooms in and out to focus given region with animation. Keeps resulting region in map bounds.
    fun focusRegionInMapBoundsAnimated(
        mainScope: CoroutineScope,
        focusRegion: MapRegion,
        minWidth: Float // min width of final region to limit max zoom level
    ) = mainScope.launch {
        cancelAnimation()
        val viewportNormalH = height / width.toFloat()
        val newRegion = focusRegion
            .expandToAspectRatio(viewportNormalH, 0f)
            .minWidth(minWidth)
            .shiftToMapBounds(TvMapRenderer.NORMAL_H)
        targetRegion = newRegion
        currentZoomAnimation = launch {
            val mapRegion = renderedMap?.region
            if (mapRegion != null) {
                val start = region
                val target = targetRegion

                val zoomingOut = newRegion.w > mapRegion.width()
                if (zoomingOut)
                    mapRenderer.update(newMapRegion = target)

                suspendCoroutine { cont ->
                    val floatEvaluator = FloatEvaluator()
                    currentAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = ZOOM_DURATION_MS
                        addUpdateListener {
                            val f = it.animatedValue as Float
                            region = MapRegion(
                                floatEvaluator.evaluate(f, start.x, target.x),
                                floatEvaluator.evaluate(f, start.y, target.y),
                                floatEvaluator.evaluate(f, start.w, target.w),
                                floatEvaluator.evaluate(f, start.h, target.h),
                            )
                            invalidate()
                        }
                        addListener(onEnd = {
                            mainScope.launch {
                                if (targetRegion == target)
                                    mapRenderer.update(newMapRegion = targetRegion)
                                currentZoomAnimation = null
                                currentAnimator = null
                                cont.resume(Unit)
                            }
                        })
                        start()
                    }
                }
            }
        }
    }

    fun setSelection(highlights: List<CountryHighlightInfo>) {
        mapRenderer.update(
            newMapRegion = region,
            newHighlights = highlights
        )
    }

    companion object {
        const val ZOOM_DURATION_MS = 300L
        const val BITMAP_MAX_PIXELS = 5_000_000 // ~2.7k
    }
}
