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

package com.protonvpn.android.ui.settings

import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.protonvpn.android.R
import com.protonvpn.android.databinding.ItemAppWithActionBinding
import com.xwray.groupie.databinding.BindableItem
import com.xwray.groupie.databinding.GroupieViewHolder

typealias AppItemAction = (packageName: String) -> Unit

class AppItemViewHolder(
    private val appItem: AppItem,
    @DrawableRes private val actionIcon: Int,
    private val onAction: AppItemAction
) : BindableItem<ItemAppWithActionBinding>() {

    override fun bind(viewBinding: ItemAppWithActionBinding, position: Int) {
        with(viewBinding) {
            textLabel.text = appItem.displayName
            buttonAction.setOnClickListener { onAction(appItem.packageName) }
            buttonAction.setImageDrawable(ContextCompat.getDrawable(root.context, actionIcon))
            Glide.with(root)
                .load(appItem.appInfo)
                .into(imageIcon)
        }
    }

    override fun unbind(viewHolder: GroupieViewHolder<ItemAppWithActionBinding>) {
        super.unbind(viewHolder)
        with(viewHolder.binding) {
            Glide.with(root).clear(imageIcon)
        }
    }

    override fun getLayout(): Int = R.layout.item_app_with_action
}
