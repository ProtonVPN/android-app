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
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.components.ContentLayout
import com.protonvpn.android.databinding.ActivityProtocolSelectionBinding
import com.protonvpn.android.databinding.ItemProtocolBinding
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.utils.ActivityResultUtils

@ContentLayout(R.layout.activity_protocol_selection)
class ProtocolSelectionActivity : BaseActivityV2<ActivityProtocolSelectionBinding, ViewModel>() {

    override fun initViewModel() {
        // No viewModel.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initToolbarWithUpEnabled(binding.contentAppbar.toolbar)
        val initialSelection = requireNotNull(getInitialSelection(intent))
        initProtocols(initialSelection)
    }

    private fun initProtocols(initialSelection: ProtocolSelection) {
        val protocolsAdapter = ProtocolsAdapter(initialSelection) { selection ->
            ActivityResultUtils.setResult(this, selection)
            finish()
        }

        with(binding.recyclerProtocols) {
            layoutManager = LinearLayoutManager(this@ProtocolSelectionActivity)
            adapter = protocolsAdapter
        }
    }

    private class ProtocolViewHolder(val binding: ItemProtocolBinding)
        : RecyclerView.ViewHolder(binding.root)

    private class ProtocolsAdapter(
        private val initialSelection: ProtocolSelection,
        private val onSelection: (ProtocolSelection) -> Unit
    ) : RecyclerView.Adapter<ProtocolViewHolder>() {

        private val items = listOf(
            ProtocolSelection(VpnProtocol.Smart, TransmissionProtocol.UDP),
            ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.UDP),
            ProtocolSelection(VpnProtocol.OpenVPN, TransmissionProtocol.UDP),
            ProtocolSelection(VpnProtocol.OpenVPN, TransmissionProtocol.TCP),
            ProtocolSelection(VpnProtocol.IKEv2, TransmissionProtocol.UDP),
        )

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
            ActivityResultUtils.createContract<ProtocolSelection, ProtocolSelection>(
                ProtocolSelectionActivity::class
            )

        fun getInitialSelection(intent: Intent): ProtocolSelection? =
            ActivityResultUtils.getInput(intent)
    }
}
