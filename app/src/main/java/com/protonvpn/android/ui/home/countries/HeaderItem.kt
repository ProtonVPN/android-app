/*
 * Copyright (c) 2019 Proton Technologies AG
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
package com.protonvpn.android.ui.home.countries

import androidx.annotation.StringRes
import com.protonvpn.android.R
import com.protonvpn.android.databinding.ItemCountryHeaderBinding
import com.xwray.groupie.databinding.BindableItem

class HeaderItem(@StringRes private val titleStringResId: Int) : BindableItem<ItemCountryHeaderBinding>() {

    override fun getLayout() = R.layout.item_country_header

    override fun bind(viewBinding: ItemCountryHeaderBinding, position: Int) {
        viewBinding.textTitle.setText(titleStringResId)
    }

    override fun getId() = titleStringResId.toLong()
}
