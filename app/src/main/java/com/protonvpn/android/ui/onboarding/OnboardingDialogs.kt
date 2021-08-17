/*
 * Copyright (c) 2018 Proton Technologies AG
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

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.protonvpn.android.R
import com.protonvpn.android.utils.Storage

object OnboardingDialogs {
    @JvmStatic
    @JvmOverloads
    fun showDialogOnView(
        context: Context?,
        view: View,
        title: String?,
        description: String?,
        booleanName: String?,
        gravity: Int = Gravity.BOTTOM
    ) {
        if (!Storage.getBoolean(booleanName)) {
            val tooltip = OnboardingTooltip.Builder(context).anchorView(view)
                .text(title)
                .gravity(gravity)
                .arrowColor(ContextCompat.getColor(view.context, R.color.white))
                .animated(true)
                .modal(true)
                .dismissOnOutsideTouch(false)
                .dismissOnInsideTouch(false)
                .transparentOverlay(true)
                .contentView(R.layout.dialog_tooltip, R.id.textTitle)
                .build()

            tooltip.show()

            (tooltip.findViewById<View>(R.id.textDescription) as TextView).text = description
            tooltip.findViewById<View>(R.id.textGotIt).setOnClickListener {
                if (tooltip.isShowing) {
                    tooltip.dismiss()
                    Storage.saveBoolean(booleanName, true)
                }
            }
        }
    }
}
