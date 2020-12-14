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
package com.protonvpn.android.utils

import android.animation.Animator
import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.core.content.res.ResourcesCompat
import com.airbnb.lottie.LottieAnimationView
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.protonvpn.android.R
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
        override fun onAnimationRepeat(animation: Animator?) {}
        override fun onAnimationStart(animation: Animator?) {}
        override fun onAnimationCancel(animation: Animator?) {}
        override fun onAnimationEnd(animation: Animator?) = onEnd(animation)
    })

fun <T> RequestBuilder<T>.addListener(
    onSuccess: (() -> Unit)? = null,
    onFail: ((e: GlideException?) -> Unit)? = null
) = listener(object : RequestListener<T> {
    override fun onLoadFailed(
        e: GlideException?,
        model: Any?,
        target: Target<T>?,
        isFirstResource: Boolean
    ): Boolean {
        onFail?.invoke(e)
        return false
    }

    override fun onResourceReady(
        resource: T?,
        model: Any?,
        target: Target<T>?,
        dataSource: DataSource?,
        isFirstResource: Boolean
    ): Boolean {
        onSuccess?.invoke()
        return false
    }
})
