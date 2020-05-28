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

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
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

    private fun convertDpToPixel(dp: Int): Int =
            (dp * Resources.getSystem().displayMetrics.density).roundToInt()

    fun convertPixelsToDp(px: Int): Float =
            px.toFloat() / Resources.getSystem().displayMetrics.density

    fun Int.toPx(): Int = convertDpToPixel(this)

    fun Int.toDp(): Float = convertPixelsToDp(this)
}
