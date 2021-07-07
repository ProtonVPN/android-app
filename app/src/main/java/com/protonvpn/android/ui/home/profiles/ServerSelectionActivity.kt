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

package com.protonvpn.android.ui.home.profiles

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.components.ContentLayout
import com.protonvpn.android.databinding.ActivityServerSelectionBinding
import com.protonvpn.android.databinding.ItemHeaderBinding
import com.protonvpn.android.databinding.ItemServerSelectionBinding
import com.protonvpn.android.ui.HeaderAdapter
import com.protonvpn.android.utils.ActivityResultUtils
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.ProtonLogger
import kotlinx.android.parcel.Parcelize
import javax.inject.Inject

@ContentLayout(R.layout.activity_server_selection)
class ServerSelectionActivity : BaseActivityV2<ActivityServerSelectionBinding, ServerSelectionViewModel>() {

    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

    override fun initViewModel() {
        viewModel =
            ViewModelProvider(this, viewModelFactory).get(ServerSelectionViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initToolbarWithUpEnabled(binding.contentAppbar.toolbar)

        val config = requireNotNull(getConfig(intent))
        setTitle(if (config.secureCore) R.string.entryCountry else R.string.serverSelection)
        val servers = viewModel.getServers(config.countryCode, config.secureCore)
        if (servers != null) {
            initServerList(config.secureCore, servers)
        } else {
            Toast.makeText(this, R.string.something_went_wrong, Toast.LENGTH_SHORT).show()
            ProtonLogger.log("No servers for country '$config.countryCode`")
            finish()
        }

    }

    fun initServerList(secureCore: Boolean, servers: List<ServerSelectionViewModel.ServerItem>) {
        val layout = LinearLayoutManager(this)
        val onSelected = { serverIdSelection: ServerIdSelection ->
            ActivityResultUtils.setResult(this, serverIdSelection)
            finish()
        }

        val combinedAdapter = ConcatAdapter(
            HeaderAdapter(R.string.recommendedHeader),
            FastestAndRandomAdapter(onSelected),
            HeaderAdapter(if (secureCore) R.string.secureCoreCountriesHeader else R.string.countryServersHeader),
            ServersAdapter(secureCore, servers, onSelected)
        )

        with(binding.recyclerServers) {
            adapter = combinedAdapter
            layoutManager = layout
        }
    }

    private class ItemViewHolder(
        val views: ItemServerSelectionBinding
    ) : RecyclerView.ViewHolder(views.root)

    private class FastestAndRandomAdapter(
        private val onSelected: (ServerIdSelection) -> Unit
    ) : RecyclerView.Adapter<ItemViewHolder>() {

        data class Item(
            @StringRes val label: Int,
            @DrawableRes val icon: Int,
            val selection: ServerIdSelection
        )

        private val items = listOf(
            Item(R.string.profileFastest, R.drawable.ic_fast, ServerIdSelection.FastestInCountry),
            Item(R.string.profileRandom, R.drawable.ic_arrows, ServerIdSelection.RandomInCountry),
        )

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
            return ItemViewHolder(
                ItemServerSelectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        }

        override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
            with(holder.views) {
                val item = items[position]
                textLabel.setText(item.label)
                imageIcon.setImageResource(item.icon)
                root.setOnClickListener { onSelected(item.selection) }
            }
        }

        override fun getItemCount(): Int = items.size
    }

    private class ServersAdapter(
        private val secureCore: Boolean,
        private val servers: List<ServerSelectionViewModel.ServerItem>,
        private val onSelected: (ServerIdSelection) -> Unit
    ) : RecyclerView.Adapter<ItemViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder =
            ItemViewHolder(
                ItemServerSelectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )

        override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
            with(holder.views) {
                val server = servers[position]
                textLabel.text = serverLabel(root.context, server)
                imageIcon.setImageResource(CountryTools.getFlagResource(root.context, server.flag))
                val trailingIcon = if (secureCore) R.drawable.ic_secure_core_arrow_green else 0
                textLabel.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, trailingIcon, 0)
                root.setOnClickListener { onSelected(ServerIdSelection.Specific(server.id)) }
                root.isEnabled = server.accessible
            }
        }

        override fun getItemCount(): Int = servers.size

        private fun serverLabel(
            context: Context,
            server: ServerSelectionViewModel.ServerItem
        ) = when {
            !server.accessible -> context.getString(R.string.serverLabelUpgrade, server.name)
            !server.online -> context.getString(R.string.serverLabelUnderMaintenance, server.name)
            else -> server.name
        }
    }

    @Parcelize
    data class Config(
        val countryCode: String,
        val secureCore: Boolean
    ) : Parcelable

    companion object {
        fun createContract() = ActivityResultUtils.createContract<Config, ServerIdSelection>(
            ServerSelectionActivity::class
        )
        private fun getConfig(intent: Intent): Config? = ActivityResultUtils.getInput(intent)
    }
}
