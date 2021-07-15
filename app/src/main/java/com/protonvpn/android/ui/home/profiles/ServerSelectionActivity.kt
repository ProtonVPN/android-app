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
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.components.ContentLayout
import com.protonvpn.android.databinding.ActivityServerSelectionBinding
import com.protonvpn.android.databinding.ItemServerSelectionBinding
import com.protonvpn.android.ui.HeaderViewHolder
import com.protonvpn.android.utils.ActivityResultUtils
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.ProtonLogger
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.OnItemClickListener
import com.xwray.groupie.OnItemLongClickListener
import com.xwray.groupie.Section
import com.xwray.groupie.databinding.BindableItem
import kotlinx.parcelize.Parcelize
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

        val recommendedSection = Section(
            HeaderViewHolder(R.string.recommendedHeader),
            listOf(
                RecommendedServerViewHolder(
                    R.string.profileFastest,
                    R.drawable.ic_fast,
                    ServerIdSelection.FastestInCountry
                ),
                RecommendedServerViewHolder(
                    R.string.profileRandom,
                    R.drawable.ic_arrows,
                    ServerIdSelection.RandomInCountry
                )
            )
        )
        val serversHeaderString =
            if (secureCore) R.string.secureCoreCountriesHeader else R.string.countryServersHeader
        val serversSection = Section(
                HeaderViewHolder(serversHeaderString),
                servers.map { ServerItemViewHolder(it, secureCore) }
        )

        val groupAdapter = GroupAdapter<GroupieViewHolder>()
        groupAdapter.add(recommendedSection)
        groupAdapter.add(serversSection)
        groupAdapter.setOnItemClickListener { item, _ ->
            val selection = (item as ServerItemViewHolderBase).selection
            ActivityResultUtils.setResult(this, selection)
            finish()
        }

        with(binding.recyclerServers) {
            adapter = groupAdapter
            layoutManager = layout
        }
    }

    private abstract class ServerItemViewHolderBase(
        val selection: ServerIdSelection
    ) : BindableItem<ItemServerSelectionBinding>() {
        override fun bind(
            viewHolder: com.xwray.groupie.databinding.GroupieViewHolder<ItemServerSelectionBinding>,
            position: Int,
            payloads: List<Any?>,
            onItemClickListener: OnItemClickListener?,
            onItemLongClickListener: OnItemLongClickListener?
        ) {
            super.bind(viewHolder, position, payloads, onItemClickListener, onItemLongClickListener)
            if (onItemClickListener != null) {
                viewHolder.binding.root.setOnClickListener {
                    onItemClickListener.onItemClick(this@ServerItemViewHolderBase, it)
                }
            }
        }
    }

    private class RecommendedServerViewHolder(
        @StringRes val label: Int,
        @DrawableRes val icon: Int,
        selection: ServerIdSelection
    ) : ServerItemViewHolderBase(selection) {

        override fun bind(viewBinding: ItemServerSelectionBinding, position: Int) {
            with(viewBinding) {
                textLabel.setText(label)
                imageIcon.setImageResource(icon)
            }
        }

        override fun getLayout(): Int = R.layout.item_server_selection
    }

    private class ServerItemViewHolder(
        private val server: ServerSelectionViewModel.ServerItem,
        private val secureCoreArrow: Boolean
    ) : ServerItemViewHolderBase(ServerIdSelection.Specific(server.id)) {

        override fun bind(viewBinding: ItemServerSelectionBinding, position: Int) {
            with(viewBinding) {
                textLabel.text = serverLabel(root.context, server)
                imageIcon.setImageResource(CountryTools.getFlagResource(root.context, server.flag))
                val trailingIcon = if (secureCoreArrow) R.drawable.ic_secure_core_arrow_green else 0
                textLabel.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, trailingIcon, 0)
                root.isEnabled = server.accessible
            }
        }

        private fun serverLabel(
            context: Context,
            server: ServerSelectionViewModel.ServerItem
        ) = when {
            !server.accessible -> context.getString(R.string.serverLabelUpgrade, server.name)
            !server.online -> context.getString(R.string.serverLabelUnderMaintenance, server.name)
            else -> server.name
        }

        override fun getLayout(): Int = R.layout.item_server_selection
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
