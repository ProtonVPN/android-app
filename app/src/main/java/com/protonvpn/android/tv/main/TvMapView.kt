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
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.R
import com.protonvpn.android.components.ContentLayout
import com.protonvpn.android.utils.inCoordsOf
import com.protonvpn.android.utils.scale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@ContentLayout(R.layout.activity_tv_main)
class TvMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var renderedMap: RenderedMap? = null
    private var region = TvMapRenderer.MapRegion(0f, 0f, 1f)
    private var targetRegion = TvMapRenderer.MapRegion(0f, 0f, 1f)
    private var viewRect = RectF(0f, 0f, 1f, 1f)

    private var currentZoomAnimation: Job? = null
    private var currentAnimator: ValueAnimator? = null

    init {
        alpha = 0f
    }

    private val mapRenderer = TvMapRenderer(context, (context as ComponentActivity).lifecycleScope) {
        if (renderedMap == null) {
            animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(MAP_SHOW_DELAY)
                .duration = MAP_FADE_IN_DURATION
        }
        renderedMap = it
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0)
            return

        renderedMap?.let { srcMap ->
            if (region.w == 0f || srcMap.region.isEmpty)
                return

            val regionRect = region.toRectF(height / width.toFloat())
            val src = regionRect
                    .inCoordsOf(srcMap.region)
                    .scale(srcMap.bitmap.width.toFloat(), srcMap.bitmap.height.toFloat())

            viewRect.set(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawBitmap(srcMap.bitmap, Rect().apply { src.round(this) }, viewRect, null)
        }
    }

    override fun layout(l: Int, t: Int, r: Int, b: Int) {
        super.layout(l, t, r, b)
        mapRenderer.updateSize(r - l, b - t)
    }

    fun setMapRegion(mainScope: CoroutineScope, newRegion: TvMapRenderer.MapRegion) = mainScope.launch {
        val shiftedRegion = newRegion.shiftedToBoundaries(height / width.toFloat())
        targetRegion = shiftedRegion
        currentAnimator?.cancel()
        currentZoomAnimation?.join()
        currentZoomAnimation = launch {
            val mapRegion = renderedMap?.region
            if (mapRegion != null) {
                val start = region.copy()
                val target = targetRegion.copy()

                val zoomingOut = shiftedRegion.w > mapRegion.width()
                if (zoomingOut)
                    mapRenderer.updateMapRegion(target.copy())

                suspendCoroutine<Unit> { cont ->
                    val floatEvaluator = FloatEvaluator()
                    currentAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = ZOOM_DURATION_MS
                        addUpdateListener {
                            val f = it.animatedValue as Float
                            region.x = floatEvaluator.evaluate(f, start.x, target.x)
                            region.y = floatEvaluator.evaluate(f, start.y, target.y)
                            region.w = floatEvaluator.evaluate(f, start.w, target.w)
                            invalidate()
                        }
                        addListener(onEnd = {
                            mainScope.launch {
                                onAnimationFinished(targetRegion == target)
                                cont.resume(Unit)
                            }
                        })
                        start()
                    }
                }
            }
        }
    }

    private suspend fun onAnimationFinished(shouldRenderTarget: Boolean) {
        if (shouldRenderTarget)
            mapRenderer.updateMapRegion(targetRegion.copy())
        currentZoomAnimation = null
        currentAnimator = null
    }

    fun setSelection(selected: String?, connected: String?) {
        mapRenderer.updateSelection(selected, connected)
    }

    companion object {
        const val ZOOM_DURATION_MS = 300L
        const val MAP_SHOW_DELAY = 500L
        const val MAP_FADE_IN_DURATION = 400L
    }
}
