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

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.databinding.ActivityRecyclerWithToolbarBinding
import com.protonvpn.android.databinding.ItemProtocolBinding
import com.protonvpn.android.utils.ActivityResultUtils
import com.protonvpn.android.vpn.ProtocolSelection
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ProtocolSelectionActivity : BaseActivityV2() {

    val viewModel: ProtocolSelectionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityRecyclerWithToolbarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initToolbarWithUpEnabled(binding.contentAppbar.toolbar)
        val initialSelection = requireNotNull(getInitialSelection(intent))
        initProtocols(binding.recyclerItems, initialSelection)
    }

    private fun initProtocols(recyclerView: RecyclerView, initialSelection: ProtocolSelection) {
        val protocolsAdapter = ProtocolsAdapter(viewModel.supportedProtocols, initialSelection) { selection ->
            ActivityResultUtils.setResult(this, selection)
            finish()
        }

        with(recyclerView) {
            layoutManager = LinearLayoutManager(this@ProtocolSelectionActivity)
            adapter = protocolsAdapter
        }
    }

    private class ProtocolViewHolder(val binding: ItemProtocolBinding)
        : RecyclerView.ViewHolder(binding.root)

    private class ProtocolsAdapter(
        private val items: List<ProtocolSelection>,
        private val initialSelection: ProtocolSelection,
        private val onSelection: (ProtocolSelection) -> Unit
    ) : RecyclerView.Adapter<ProtocolViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProtocolViewHolder =
            ProtocolViewHolder(
                ItemProtocolBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: ProtocolViewHolder, position: Int) {
            with(holder.binding.radioProtocol) {
                val item = items[position]
                setText(item.displayName)
                isChecked = initialSelection == item
                setOnClickListener { onSelection(item) }
            }
        }

        override fun getItemCount(): Int = items.size
    }

    companion object {
        fun createContract() =
            ActivityResultUtils.createSerializableContract<ProtocolSelection, ProtocolSelection>(
                ProtocolSelectionActivity::class
            )

        fun getInitialSelection(intent: Intent): ProtocolSelection? =
            ActivityResultUtils.getSerializableInput(intent)
    }
}
