/*
 * Copyright (c) 2017 Proton Technologies AG
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
package com.protonvpn.android.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.AnimationUtils
import androidx.annotation.ColorRes
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.protonvpn.android.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TriangledTextView(context: Context?, attrs: AttributeSet?) : AppCompatTextView(context, attrs) {

    private val paint = Paint()
    private val path = Path()
    private val mirror = Matrix()
    private var isExpanded = true
    private var hideJob: Job? = null

    init {
        paint.color = ContextCompat.getColor(getContext(), R.color.colorAccent)
    }

    override fun onDraw(canvas: Canvas) {
        with(path) {
            reset()
            moveTo(.0f, 0.5f * height)
            lineTo(50f, height.toFloat())
            lineTo(width.toFloat(), height.toFloat())
            lineTo(width.toFloat(), .0f)
            lineTo(50f, .0f)
            lineTo(.0f, 0.5f * height)
        }
        if (layoutDirection == View.LAYOUT_DIRECTION_RTL) {
            mirror.setRotate(180f, width / 2.toFloat(), height / 2.toFloat())
            path.transform(mirror)
        }
        canvas.clipPath(path)
        canvas.drawPath(path, paint)
        super.onDraw(canvas)
    }

    fun setExpanded(expand: Boolean, animate: Boolean, scope: CoroutineScope) {
        if (isExpanded != expand) {
            isExpanded = expand
            isClickable = expand

            hideJob?.cancel()
            clearAnimation()
            if (!animate) {
                setExpandedNoAnim(expand)
            } else {
                isVisible = true
                val anim = AnimationUtils.loadAnimation(context, if (expand)
                    R.anim.slide_in_from_end else R.anim.slide_out_to_end)
                anim.duration = 300
                if (!expand) {
                    hideJob = scope.launch {
                        // If animation is canceled (e.g. because of view going offscreen) we'll end
                        // up in initial state of view (and not a target one), make sure that after
                        // animation is finished view is in the target state.
                        delay(anim.duration + 50)
                        setExpandedNoAnim(isExpanded)
                        hideJob = null
                    }
                }
                startAnimation(anim)
            }
        }
    }

    private fun setExpandedNoAnim(expand: Boolean) {
        isVisible = expand
        if (expand) {
            alpha = 1f
            translationX = 0f
        }
    }

    fun setColor(@ColorRes color: Int) {
        if (paint.color != ContextCompat.getColor(context, color)) {
            paint.color = ContextCompat.getColor(context, color)
            invalidate()
        }
    }
}
