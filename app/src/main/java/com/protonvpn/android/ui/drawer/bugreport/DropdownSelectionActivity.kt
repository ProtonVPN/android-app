/*
 * Copyright (c) 2022 Proton AG
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
package com.protonvpn.android.ui.drawer.bugreport

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.databinding.ActivityRecyclerWithToolbarBinding
import com.protonvpn.android.databinding.ItemDropdownSelectionBinding
import com.protonvpn.android.utils.ActivityResultUtils
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DropdownSelectionActivity : BaseActivityV2() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityRecyclerWithToolbarBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (isTv()) {
            binding.contentAppbar.toolbar.visibility = View.GONE
        } else {
            initToolbarWithUpEnabled(binding.contentAppbar.toolbar)
        }

        initDropdownSelections(binding.recyclerItems, requireNotNull(getSelections(intent)))
    }

    private fun initDropdownSelections(recyclerView: RecyclerView, selectionList: DropdownSelectionList) {
        val selectionAdapter = SelectionAdapter(selectionList) { selection ->
            ActivityResultUtils.setResult(this, selection)
            finish()
        }

        with(recyclerView) {
            layoutManager = LinearLayoutManager(this@DropdownSelectionActivity)
            adapter = selectionAdapter
        }
    }

    private class SelectionViewHolder(val binding: ItemDropdownSelectionBinding)
        : RecyclerView.ViewHolder(binding.root)

    private class SelectionAdapter(
        initialItems: DropdownSelectionList,
        private val onSelection: (DropdownSelection) -> Unit
    ) : RecyclerView.Adapter<SelectionViewHolder>() {

        private val items = initialItems.selectionList

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectionViewHolder =
            SelectionViewHolder(
                ItemDropdownSelectionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: SelectionViewHolder, position: Int) {
            with(holder.binding.radioDropdownSelection) {
                val item = items[position]
                text = item.displayName
                setOnClickListener { onSelection(item) }
            }
        }

        override fun getItemCount(): Int = items.size
    }

    companion object {
        fun createContract() =
            ActivityResultUtils.createContract<DropdownSelectionList, DropdownSelection>(
                DropdownSelectionActivity::class
            )

        fun getSelections(intent: Intent): DropdownSelectionList? =
            ActivityResultUtils.getInput(intent)
    }
}
