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

package com.protonvpn.android.ui.snackbar

import android.content.res.Resources
import android.view.View
import androidx.annotation.StringRes
import com.google.android.material.snackbar.Snackbar
import me.proton.core.presentation.utils.SnackType
import me.proton.core.presentation.utils.snack

/**
 * An Activity helper for easy display of properly anchored snackbars.
 */
open class SnackbarHelper(private val resources: Resources, private val view: View) {
    var anchorView: View? = null

    fun errorSnack(@StringRes messageRes: Int) = errorSnack(resources.getString(messageRes))
    fun errorSnack(message: String) = showSnack(message, SnackType.Error)

    fun snack(@StringRes messageRes: Int, type: SnackType) =
        showSnack(resources.getString(messageRes), type)

    protected fun showSnack(
        message: String,
        type: SnackType,
        length: Int = Snackbar.LENGTH_LONG,
        configBlock: (Snackbar.() -> Unit)? = null
    ) = view.snack(message, type, length) {
        anchorView = this@SnackbarHelper.anchorView
        configBlock?.invoke(this)
    }
}
