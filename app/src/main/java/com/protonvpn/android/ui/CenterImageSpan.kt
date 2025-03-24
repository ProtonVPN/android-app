/*
 * Copyright (c) 2021. Proton AG
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

package com.protonvpn.android.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.style.DynamicDrawableSpan
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.withTranslation
import java.text.Bidi

// A span that displays an image vertically aligned.
class CenterImageSpan(context: Context, resourceId: Int) : DynamicDrawableSpan() {

    private val imageDrawable =
        requireNotNull(AppCompatResources.getDrawable(context, resourceId)).mutate().apply {
            setBounds(0, 0, intrinsicWidth, intrinsicHeight)
        }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        if (text == null) return

        val translateY = top + (bottom - top) / 2 - drawable.bounds.height() / 2

        val string = text.toString()
        drawable.layoutDirection = View.LAYOUT_DIRECTION_LTR
        if (Bidi.requiresBidi(string.toCharArray(), 0, text.length)) {
            val bidi = Bidi(string, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT)
            if (bidi.getLevelAt(start) == 1) {
                drawable.layoutDirection = View.LAYOUT_DIRECTION_RTL            }
        }

        canvas.withTranslation(x, translateY.toFloat()) {
            drawable.draw(canvas)
        }
    }

    override fun getDrawable(): Drawable = imageDrawable
}
