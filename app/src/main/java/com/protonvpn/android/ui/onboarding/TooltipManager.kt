/*
 * Copyright (c) 2021. Proton Technologies AG
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

package com.protonvpn.android.ui.onboarding

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.annotation.ColorInt
import androidx.core.animation.doOnEnd
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.core.view.doOnLayout
import com.google.android.material.color.MaterialColors
import com.protonvpn.android.R

private const val FADE_DURATION_MS = 300L

typealias OnTooltipDismissed = () -> Unit

class TooltipManager(val activity: Activity) {

    private var isTooltipDisplayed = false

    /**
     * Shows a tooltip anchored at anchorView. The bounds of highlightView are not covered by the
     * scrim so that it looks highlighted.
     */
    fun show(
        highlightView: View,
        anchorView: View,
        titleText: CharSequence,
        tooltipText: CharSequence,
        onDismissedAction: OnTooltipDismissed? = null
    ) {
        prepareTooltip(titleText, tooltipText, onDismissedAction)?.also {
            it.show(highlightView, anchorView)
        }
    }

    /**
     * Shows a tooltip anchored at highlightView.
     * The scrim covers the whole screen with replacementViews positioned above it at the location
     * of highlightView. This allows for replacing the view to be highlighted with a similar or
     * identical view on top of the scrim.
     */
    fun showWithReplacement(
        highlightView: View,
        replacementViews: Array<View>,
        titleText: CharSequence,
        tooltipText: CharSequence,
        onShown: (() -> Unit)?,
        onDismissedAction: OnTooltipDismissed?
    ) {
        prepareTooltip(titleText, tooltipText, onDismissedAction)?.also {
            it.showWithReplacement(highlightView, replacementViews, onShown)
        }
    }

    private fun prepareTooltip(
        titleText: CharSequence,
        tooltipText: CharSequence,
        onDismissedAction: OnTooltipDismissed?
    ): Tooltip? {
        if (isTooltipDisplayed)
            return null

        val hostView = createTooltipContainer(activity)
        return Tooltip(hostView, titleText, tooltipText) {
            isTooltipDisplayed = false
            onDismissedAction?.invoke()
        }.also {
            isTooltipDisplayed = true
        }
    }

    class Tooltip(
        private val hostView: ViewGroup,
        private val titleText: CharSequence,
        private val tooltipText: CharSequence,
        private val onDismissedAction: OnTooltipDismissed?
    ) {
        private var scrim: View? = null
        private var popup: PopupWindow? = null

        fun show(highlightView: View, anchorView: View) {
            hostView.doOnLayout {
                anchorView.doOnLayout {
                    showScrimWithHole(hostView, highlightView, onDismissedAction)
                    showPopup(anchorView, titleText, tooltipText, onDismissedAction)
                }
            }
        }

        /**
         * Shows a tooltip anchored at highlightView.
         * The scrim covers the whole screen with replacementViews positioned above it at the location
         * of highlightView. This allows for replacing the view to be highlighted with a similar or
         * identical view on top of the scrim.
         */
        fun showWithReplacement(highlightView: View, replacementViews: Array<View>, onShown: (() -> Unit)?) {
            hostView.doOnLayout {
                onShown?.invoke()
                showScrimWithReplacement(highlightView, replacementViews, onDismissedAction)
                showPopup(highlightView, titleText, tooltipText, onDismissedAction)
            }
        }

        private fun showScrimWithHole(
            hostView: ViewGroup,
            highlightView: View,
            onDismissedAction: OnTooltipDismissed?
        ) {
            val context = hostView.context

            val viewLocation = highlightView.offset(hostView)
            val highlightRect = Rect(
                viewLocation.x,
                viewLocation.y,
                viewLocation.x + highlightView.width,
                viewLocation.y + highlightView.height
            )
            val scrimDrawable = OverlayDrawable(
                highlightRect,
                MaterialColors.getColor(hostView, R.attr.proton_blender_norm)
            )

            val newScrim = createScrim(context, scrimDrawable, onDismissedAction)
            scrim = newScrim
            hostView.addView(newScrim)

            newScrim.fade(true)
        }

        private fun showScrimWithReplacement(
            highlightView: View,
            replacementViews: Array<View>,
            onDismissedAction: OnTooltipDismissed?
        ) {
            val context = hostView.context

            val background =
                ColorDrawable(MaterialColors.getColor(hostView, R.attr.proton_blender_norm))
            val newScrim = createScrim(context, background, onDismissedAction)
            scrim = newScrim
            hostView.addView(newScrim)

            replacementViews.forEach { view ->
                view.copyWindowBoundsOf(highlightView, hostView)
                hostView.addView(view)
            }

            newScrim.fade(true)
        }


        private fun showPopup(
            anchorView: View,
            titleText: CharSequence,
            tooltipText: CharSequence,
            onDismissedAction: OnTooltipDismissed?
        ) {
            val tooltip = createTooltip(hostView.context, titleText, tooltipText, onDismissedAction)
            popup = createAndShowPopup(tooltip, anchorView)
        }

        private fun hide(onDismissed: OnTooltipDismissed?) {
            popup?.dismiss()
            scrim?.fade(false)?.doOnEnd {
                (hostView.parent as ViewGroup).removeView(hostView)
                onDismissed?.invoke()
            }
            popup = null
            scrim = null
        }

        private fun createTooltip(
            context: Context,
            title: CharSequence,
            text: CharSequence,
            onDismissed: OnTooltipDismissed?
        ) = TooltipView(context).apply {
            textTitle.text = title
            textMessage.text = text
            setOnAcknowledgedListener(View.OnClickListener { hide(onDismissed) })

            val tooltipMargin =
                context.resources.getDimensionPixelSize(R.dimen.screen_padding_horizontal)
            val tooltipWidth = hostView.width - 2 * tooltipMargin
            measure(
                View.MeasureSpec.makeMeasureSpec(tooltipWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
        }


        @SuppressLint("RtlHardcoded")
        private fun createAndShowPopup(tooltip: TooltipView, anchorView: View): PopupWindow {
            val tooltipWidth = tooltip.measuredWidth
            val tooltipHeight = tooltip.measuredHeight
            val tooltipMargin = (hostView.width - tooltipWidth) / 2

            // The PopupWindow's position is in window coordinates.
            val windowHeight = hostView.locationInWindow().y + hostView.height

            val anchorViewInWindow = anchorView.locationInWindow()
            val anchorOffsetBelow = Point(anchorViewInWindow)
            anchorOffsetBelow.offset(0, anchorView.height)

            val anchorOffset: Point
            val showBelow = tooltipHeight <= windowHeight - anchorOffsetBelow.y
            if (showBelow) {
                anchorOffset = anchorOffsetBelow
                anchorOffset.offset(anchorView.width / 2, 0)
            } else {
                anchorOffset = Point(anchorViewInWindow)
                anchorOffset.offset(anchorView.width / 2, -tooltipHeight)
            }
            tooltip.setArrowPosition(anchorOffset.x - tooltipMargin, !showBelow)

            return PopupWindow(tooltip).apply {
                width = tooltipWidth
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                animationStyle = android.R.style.Animation_Dialog
                showAtLocation(hostView, Gravity.LEFT or Gravity.TOP, tooltipMargin, anchorOffset.y)
            }
        }

        private fun createScrim(
            context: Context,
            scrimBg: Drawable,
            onDismissedAction: OnTooltipDismissed?
        ): View = View(context).apply {
            background = scrimBg
            right = hostView.width
            bottom = hostView.height
            setOnClickListener { hide(onDismissedAction) }
        }

        private fun View.copyWindowBoundsOf(other: View, hostView: View) {
            val hostOffset = other.offset(hostView)
            layout(hostOffset.x, hostOffset.y, hostOffset.x + other.width, hostOffset.y + other.height)
        }

        private fun View.offset(other: View): Point {
            val position = intArrayOf(0, 0)
            val otherPosition = intArrayOf(0, 0)
            getLocationInWindow(position)
            other.getLocationInWindow(otherPosition)
            return Point(position[0] - otherPosition[0], position[1] - otherPosition[1])
        }

        private fun View.locationInWindow(): Point {
            val position = intArrayOf(0, 0)
            getLocationInWindow(position)
            return Point(position[0], position[1])
        }

        private fun View.fade(show: Boolean): ValueAnimator {
            val startAlpha = if (show) 0f else 1f
            val endAlpha = if (show) 1f else 0f
            alpha = startAlpha
            return ObjectAnimator.ofFloat(this, View.ALPHA, startAlpha, endAlpha).apply {
                duration = FADE_DURATION_MS
                start()
            }
        }
    }

    private fun createTooltipContainer(activity: Activity): ViewGroup {
        val layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        val contentView = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
        val tooltipLayout = TooltipsLayout(activity)
        contentView.addView(tooltipLayout, layoutParams)
        return tooltipLayout
    }

    class OverlayDrawable(private val holeRect: Rect, @ColorInt color: Int) : Drawable() {

        private var bgBitmap: Bitmap? = null

        private val erasarPaint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }

        private val paint = Paint().apply {
            colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                color,
                BlendModeCompat.SRC_IN
            )
        }

        override fun draw(canvas: Canvas) {
            val bitmap = getBitmap(bounds)
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

        private fun getBitmap(bounds: Rect): Bitmap =
            bgBitmap
                ?.takeIf { it.width != bounds.width() || it.height != bounds.height() }
                ?: makeBitmap(bounds).also { newBitmap ->
                    bgBitmap = newBitmap
                }

        private fun makeBitmap(bounds: Rect): Bitmap {
            val bitmap = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ALPHA_8)
            val canvas = Canvas(bitmap)
            canvas.drawColor(0xff000000.toInt())
            canvas.drawRect(holeRect, erasarPaint)
            return bitmap
        }
    }
}
