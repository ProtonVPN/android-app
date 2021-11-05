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

package com.protonvpn.android.ui.snackbar

import android.content.res.Resources
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.google.android.material.snackbar.Snackbar
import com.protonvpn.android.R
import me.proton.core.presentation.utils.snack

/**
 * An Activity helper for easy display of properly anchored snackbars.
 */
open class SnackbarHelper(private val resources: Resources, private val view: View) {
    var anchorView: View? = null

    fun successSnack(@StringRes messageRes: Int) = successSnack(resources.getString(messageRes))
    fun successSnack(message: String) = showSnack(message, R.drawable.snackbar_background_success)

    fun errorSnack(@StringRes messageRes: Int) = errorSnack(resources.getString(messageRes))
    fun errorSnack(message: String) = showSnack(message, R.drawable.snackbar_background_error)

    protected fun showSnack(
        message: String,
        @DrawableRes background: Int,
        length: Int = Snackbar.LENGTH_LONG,
        configBlock: (Snackbar.() -> Unit)? = null
    ) = view.snack(message, background, length) {
        anchorView = this@SnackbarHelper.anchorView
        configBlock?.invoke(this)
    }
}
