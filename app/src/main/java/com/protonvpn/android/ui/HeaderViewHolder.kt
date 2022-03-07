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

package com.protonvpn.android.ui

import android.view.View
import androidx.annotation.StringRes
import com.protonvpn.android.R
import com.protonvpn.android.databinding.ItemHeaderBinding
import com.protonvpn.android.utils.DebugUtils
import com.xwray.groupie.viewbinding.BindableItem

data class HeaderViewHolder(
    @StringRes private val textRes: Int = 0,
    private val text: String? = null,
    private val itemId: Long = 1
) : BindableItem<ItemHeaderBinding>(itemId) {

    init {
        DebugUtils.debugAssert("One of text and textRes needs to be defined") {
            textRes != 0 || text != null
        }
    }

    override fun bind(viewBinding: ItemHeaderBinding, position: Int) {
        if (text != null) {
            viewBinding.textHeader.text = text
        } else {
            viewBinding.textHeader.setText(textRes)
        }
    }

    override fun initializeViewBinding(view: View): ItemHeaderBinding = ItemHeaderBinding.bind(view)

    override fun getLayout(): Int = R.layout.item_header

    override fun isClickable(): Boolean = false
}
