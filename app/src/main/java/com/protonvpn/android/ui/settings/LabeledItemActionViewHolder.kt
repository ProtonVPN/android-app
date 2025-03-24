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

package com.protonvpn.android.ui.settings

import android.view.View
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.protonvpn.android.R
import com.protonvpn.android.databinding.ItemLabelWithActionButtonBinding
import com.protonvpn.android.utils.setMinSizeTouchDelegate
import com.xwray.groupie.viewbinding.BindableItem
import com.xwray.groupie.viewbinding.GroupieViewHolder

typealias LabeledItemAction = (item: LabeledItem) -> Unit

data class LabeledItemActionViewHolder(
    private val item: LabeledItem,
    @DrawableRes private val actionIcon: Int,
    private val onAction: LabeledItemAction
) : BindableItem<ItemLabelWithActionButtonBinding>() {

    override fun createViewHolder(itemView: View): GroupieViewHolder<ItemLabelWithActionButtonBinding> {
        return super.createViewHolder(itemView).apply {
            binding.buttonAction.setMinSizeTouchDelegate()
        }
    }

    override fun bind(viewBinding: ItemLabelWithActionButtonBinding, position: Int) {
        with(viewBinding) {
            textLabel.text = item.label
            buttonAction.setOnClickListener { onAction(item) }
            buttonAction.setImageDrawable(ContextCompat.getDrawable(root.context, actionIcon))
            if (item.iconDrawable == null) {
                textLabel.setCompoundDrawablesRelativeWithIntrinsicBounds(item.iconRes, 0, 0, 0)
            } else {
                textLabel.setCompoundDrawablesRelative(item.iconDrawable, null, null, null)
            }
        }
    }

    override fun getId(): Long = item.id.hashCode().toLong()

    override fun getLayout(): Int = R.layout.item_label_with_action_button

    override fun initializeViewBinding(view: View) = ItemLabelWithActionButtonBinding.bind(view)
}
