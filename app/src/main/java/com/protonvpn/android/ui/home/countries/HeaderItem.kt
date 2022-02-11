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

import android.view.View
import androidx.core.view.isVisible
import com.protonvpn.android.R
import com.protonvpn.android.databinding.ItemCountryHeaderBinding
import com.protonvpn.android.ui.home.InformationActivity
import com.protonvpn.android.utils.getThemeColor
import com.protonvpn.android.utils.setMinSizeTouchDelegate
import com.xwray.groupie.viewbinding.BindableItem

class HeaderItem(
    private val titleString: String,
    private val countryInfoKey: String?,
    private val isServer: Boolean,
) : BindableItem<ItemCountryHeaderBinding>() {

    override fun getLayout() = R.layout.item_country_header

    override fun initializeViewBinding(view: View) = ItemCountryHeaderBinding.bind(view)

    override fun bind(viewBinding: ItemCountryHeaderBinding, position: Int) {
        viewBinding.textTitle.text = titleString
        with(viewBinding.serversInfo) {
            setMinSizeTouchDelegate()
            isVisible = countryInfoKey != null
            if (countryInfoKey != null) setOnClickListener {
                context.startActivity(InformationActivity.createIntent(context, countryInfoKey))
            } else {
                setOnClickListener(null)
            }
        }
        with(viewBinding.root) {
            setBackgroundColor(
                getThemeColor(
                    if (isServer) R.attr.proton_background_secondary else R.attr.proton_background_norm
                )
            )
        }
    }

    override fun getId() = titleString.hashCode().toLong()
}
