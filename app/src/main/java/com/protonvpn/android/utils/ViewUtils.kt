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
package com.protonvpn.android.utils

import android.animation.Animator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.graphics.Rect
import android.os.Build
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.TouchDelegate
import android.view.View
import android.view.ViewPropertyAnimator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.viewbinding.ViewBinding
import com.airbnb.lottie.LottieAnimationView
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.color.MaterialColors
import com.protonvpn.android.R
import com.protonvpn.android.utils.ViewUtils.toPx
import kotlin.math.roundToInt

object ViewUtils {

    fun Activity.showKeyboard(edit: EditText) {
        if (window != null) {
            edit.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun Activity.hideKeyboard(on: View? = null) {
        if (window != null) {
            val view: View? = on ?: currentFocus ?: window.decorView.rootView
            if (view != null) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
            }
        }
    }

    inline fun <VB : ViewBinding> Activity.viewBinding(crossinline inflater: (LayoutInflater) -> VB) =
        lazy(LazyThreadSafetyMode.NONE) { inflater(layoutInflater) }

    fun View.requestAllFocus() {
        requestFocus()
        requestFocusFromTouch()
    }

    // Lolipop can't into android:foreground
    fun View.initLolipopButtonFocus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            val focusDrawable =
                ResourcesCompat.getDrawable(resources, R.drawable.tv_focus_foreground_focused, null)!!
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                focusDrawable.setBounds(0, 0, width, height)
            }
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus)
                    overlay.add(focusDrawable)
                else
                    overlay.clear()
            }
        }
    }

    private fun convertDpToPixel(dp: Int): Int =
            (dp * Resources.getSystem().displayMetrics.density).roundToInt()

    fun convertPixelsToDp(px: Int): Float =
            px.toFloat() / Resources.getSystem().displayMetrics.density

    fun Int.toPx(): Int = convertDpToPixel(this)

    fun Int.toDp(): Float = convertPixelsToDp(this)
}

fun LottieAnimationView.onAnimationEnd(onEnd: (Animator?) -> Unit) =
    addAnimatorListener(object : Animator.AnimatorListener {
        override fun onAnimationRepeat(animation: Animator) {}
        override fun onAnimationStart(animation: Animator) {}
        override fun onAnimationCancel(animation: Animator) {}
        override fun onAnimationEnd(animation: Animator) = onEnd(animation)
    })

fun <T> RequestBuilder<T>.addListener(
    onSuccess: (() -> Unit)? = null,
    onFail: ((e: GlideException?) -> Unit)? = null
) = listener(object : RequestListener<T> {
    override fun onLoadFailed(
        e: GlideException?,
        model: Any?,
        target: Target<T>,
        isFirstResource: Boolean
    ): Boolean {
        onFail?.invoke(e)
        return false
    }

    override fun onResourceReady(
        resource: T?,
        model: Any?,
        target: Target<T?>?,
        dataSource: DataSource?,
        isFirstResource: Boolean
    ): Boolean {
        onSuccess?.invoke()
        return false
    }
})

fun ViewPropertyAnimator.whenCancelled(action: () -> Unit) {
    setListener(object : Animator.AnimatorListener {
        override fun onAnimationStart(animation: Animator) {}
        override fun onAnimationEnd(animation: Animator) {}
        override fun onAnimationRepeat(animation: Animator) {}
        override fun onAnimationCancel(animation: Animator) {
            action()
        }
    })
}

fun View.getSelectableItemBackgroundRes() = with(TypedValue()) {
    context.theme.resolveAttribute(android.R.attr.selectableItemBackground, this, true)
    resourceId
}

@ColorInt
fun View.getThemeColor(@AttrRes attr: Int): Int = MaterialColors.getColor(this, attr)

@ColorRes
fun View.getThemeColorId(@AttrRes attr: Int): Int = context.getThemeColorId(attr)

fun View.setMinSizeTouchDelegate() {
    doOnLayout {
        val minSizePx = 48.toPx()
        val rect = Rect()
        getHitRect(rect)

        if (rect.expandTo(minSizePx, minSizePx)) {
            (parent as View).touchDelegate = TouchDelegate(rect, this)
        }
    }
}

@SuppressLint("ClickableViewAccessibility")
fun View.preventClickTrough() {
    setOnTouchListener { _, _ -> /* capture all events */ true }
}

fun TextView.setTextOrGoneIfNullOrEmpty(newText: CharSequence?) {
    if (!newText.isNullOrEmpty()) {
        visibility = View.VISIBLE
        text = newText
    } else {
        visibility = View.GONE
    }
}

fun applySystemBarInsets(
    view: View,
    applyPadding: (View, Insets) -> Unit = { v, insets -> v.updatePadding(top = insets.top, bottom = insets.bottom) }
) {
    ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
        val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
        applyPadding(v, insets)
        windowInsets.inset(insets)
    }
}

fun NestedScrollView.scrollToShowView(child: View) {
    val scrollBounds = Rect()
    this.getDrawingRect(scrollBounds)
    val childTop = child.y.roundToInt()
    val childBottom = childTop + child.height
    val scrollByVertically = when {
        scrollBounds.bottom < childBottom -> childBottom - scrollBounds.bottom
        scrollBounds.top > childTop -> scrollBounds.top - childTop
        else -> 0
    }
    smoothScrollBy(0, scrollByVertically)
}

private fun Rect.expandTo(minWidth: Int, minHeight: Int): Boolean {
    var hasChanged = false
    if (width() < minWidth) {
        val leftOffset = (minWidth - width()) / 2
        left -= leftOffset
        right = left + minWidth
        hasChanged = true
    }
    if (height() < minHeight) {
        val topOffset = (minHeight - height()) / 2
        top -= topOffset
        bottom = top + minHeight
        hasChanged = true
    }
    return hasChanged
}
