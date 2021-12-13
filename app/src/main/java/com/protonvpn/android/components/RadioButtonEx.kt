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

package com.protonvpn.android.components

import android.content.Context
import android.util.AttributeSet
import me.proton.core.presentation.ui.view.ProtonRadioButton

class RadioButtonEx @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ProtonRadioButton(context, attrs) {

    var switchClickInterceptor: (RadioButtonEx.() -> Boolean)? = null

    override fun performClick(): Boolean {
        if (!isChecked && switchClickInterceptor?.invoke(this) == true)
            return true
        return super.performClick()
    }
}
