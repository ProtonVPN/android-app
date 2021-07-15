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

import androidx.annotation.StringRes
import com.protonvpn.android.R
import com.protonvpn.android.databinding.ItemHeaderBinding
import com.xwray.groupie.databinding.BindableItem

class HeaderViewHolder(
    @StringRes private val text: Int
) : BindableItem<ItemHeaderBinding>() {

    override fun bind(viewBinding: ItemHeaderBinding, position: Int) {
        viewBinding.textHeader.setText(text)
    }

    override fun getLayout(): Int = R.layout.item_header
}
