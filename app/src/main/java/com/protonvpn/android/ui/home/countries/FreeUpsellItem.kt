/*
 * Copyright (c) 2023. Proton AG
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

import android.view.View
import com.protonvpn.android.R
import com.protonvpn.android.databinding.ItemFreeUpsellBinding
import com.xwray.groupie.viewbinding.BindableItem

class FreeUpsellItem(
    private val countryCount: Int, private val onClick: () -> Unit
) : BindableItem<ItemFreeUpsellBinding>() {
    override fun bind(binding: ItemFreeUpsellBinding, position: Int) = with(binding) {
        val resources = root.resources
        textTitle.text = resources.getString(R.string.free_upsell_header_title, countryCount)
        root.setOnClickListener { onClick() }
    }

    override fun getLayout(): Int = R.layout.item_free_upsell
    override fun initializeViewBinding(view: View) = ItemFreeUpsellBinding.bind(view)
}
