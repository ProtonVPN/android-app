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

package com.protonvpn.android.ui.splittunneling

import androidx.core.view.isVisible
import com.protonvpn.android.R
import com.protonvpn.android.databinding.ItemAppBinding
import com.protonvpn.android.databinding.ItemAppsHeaderBinding
import com.protonvpn.android.databinding.ItemAppsLoadSystemAppsBinding
import com.protonvpn.android.utils.BindableItemEx
import com.xwray.groupie.databinding.BindableItem

class AppViewHolder(
    private val item: SelectedApplicationEntry,
    private val onAdd: (SelectedApplicationEntry) -> Unit,
    private val onRemove: (SelectedApplicationEntry) -> Unit
) : BindableItemEx<ItemAppBinding>() {

    override fun bind(viewBinding: ItemAppBinding, position: Int) {
        super.bind(viewBinding, position)
        with(viewBinding) {
            imageIcon.setImageDrawable(item.icon)
            textName.text = item.toString()
            textAdd.setOnClickListener {
                onAdd(item)
                toggleSelection()
            }
            clearIcon.setOnClickListener {
                onRemove(item)
                toggleSelection()
            }
            updateSelection()
        }
    }

    private fun toggleSelection() {
        item.isSelected = !item.isSelected
        updateSelection()
    }

    private fun updateSelection() {
        with(binding) {
            clearIcon.isVisible = item.isSelected
            textAdd.isVisible = !item.isSelected
        }
    }

    override fun getLayout(): Int = R.layout.item_app

    override fun clear() {
    }
}

class AppsHeaderViewHolder(private val titleRes: Int)
    : BindableItem<ItemAppsHeaderBinding>() {
    override fun bind(viewBinding: ItemAppsHeaderBinding, position: Int) {
        viewBinding.textHeader.setText(titleRes)
    }

    override fun getLayout(): Int = R.layout.item_apps_header
}

class LoadSystemAppsViewHolder(
    private val onLoadClicked: () -> Unit
) : BindableItem<ItemAppsLoadSystemAppsBinding>() {

    override fun bind(viewBinding: ItemAppsLoadSystemAppsBinding, position: Int) {
        with(viewBinding) {
            buttonLoadSystemApps.setOnClickListener {
                buttonLoadSystemApps.isVisible = false
                progress.isVisible = true
                onLoadClicked()
            }
        }
    }

    override fun getLayout(): Int = R.layout.item_apps_load_system_apps
}
