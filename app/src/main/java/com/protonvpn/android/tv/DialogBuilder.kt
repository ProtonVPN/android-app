/*
 * Copyright (c) 2024. Proton AG
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

package com.protonvpn.android.tv

import android.content.Context
import androidx.appcompat.view.ContextThemeWrapper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.protonvpn.android.R

fun showTvDialog(context: Context, focusedButton: Int, builder: MaterialAlertDialogBuilder.() -> Unit) {
    MaterialAlertDialogBuilder(ContextThemeWrapper(context, R.style.ProtonTheme_Vpn_TvDialog))
        .apply(builder)
        .create()
        .apply {
            setOnShowListener {
                getButton(focusedButton).requestFocus()
            }
        }
        .show()
}
