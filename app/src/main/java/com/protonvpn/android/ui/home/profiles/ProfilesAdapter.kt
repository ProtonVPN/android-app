/*
 * Copyright (c) 2017 Proton Technologies AG
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

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.RecyclerView
import com.protonvpn.android.R
import com.protonvpn.android.bus.ConnectToProfile
import com.protonvpn.android.bus.EventBus
import com.protonvpn.android.components.BaseViewHolderV2
import com.protonvpn.android.databinding.ItemProfileListBinding
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.ui.home.profiles.ProfileActivity.Companion.navigateForEdit
import com.protonvpn.android.ui.home.profiles.ProfilesAdapter.ServersViewHolder
import com.protonvpn.android.utils.getSelectableItemBackgroundRes
import com.protonvpn.android.utils.getThemeColor
import com.protonvpn.android.vpn.VpnStateMonitor

class ProfilesAdapter(
    private val profilesFragment: ProfilesFragment,
    private val profilesViewModel: ProfilesViewModel,
    private val parentLifeCycle: LifecycleOwner,
) : RecyclerView.Adapter<ServersViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ServersViewHolder(ItemProfileListBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ServersViewHolder, position: Int) {
        holder.bindData(profilesViewModel.getProfile(position))
    }

    override fun onViewRecycled(holder: ServersViewHolder) {
        holder.unbind()
    }

    override fun getItemCount() = profilesViewModel.profileCount

    inner class ServersViewHolder(binding: ItemProfileListBinding) :
        BaseViewHolderV2<Profile, ItemProfileListBinding>(binding) {

        private val vpnStateObserver = Observer<VpnStateMonitor.Status> {
            val server = item.server
            val connected = profilesViewModel.isConnectedTo(server)
            val colorAttr = if (connected) R.attr.colorAccent else R.attr.proton_interaction_weak
            binding.buttonConnect.backgroundTintList =
                ColorStateList.valueOf(binding.root.getThemeColor(colorAttr))
        }

        override fun bindData(newItem: Profile) = with(binding) {
            super.bindData(newItem)
            val server = newItem.server
            val online = server?.online == true

            textServer.text = newItem.getDisplayName(textServer.context)

            val hasAccess = profilesViewModel.hasAccessToServer(server)
            buttonUpgrade.isVisible = !hasAccess && server != null
            buttonConnect.isVisible = hasAccess && online
            imageWrench.isVisible = hasAccess && !online
            buttonConnect.contentDescription = textServer.text

            val editClickListener = View.OnClickListener {
                navigateForEdit(profilesFragment, item)
            }
            val connectUpgradeClickListener = View.OnClickListener {
                val connectTo = if (profilesViewModel.isConnectedTo(server)) null else item
                EventBus.post(ConnectToProfile(connectTo))
            }
            buttonConnect.setOnClickListener(connectUpgradeClickListener)
            buttonUpgrade.setOnClickListener(connectUpgradeClickListener)
            buttonUpgrade.contentDescription = textServer.text
            textServerNotSet.isVisible = server == null
            profileEditButton.isVisible = !newItem.isPreBakedProfile
            profileColor.setBackgroundColor(
                newItem.colorString?.let { Color.parseColor(it) } ?: Color.TRANSPARENT
            )
            profileEditButton.setOnClickListener(editClickListener)
            profileItem.setOnClickListener(if (newItem.isPreBakedProfile) null else editClickListener)
            profileItem.setBackgroundResource(if (newItem.isPreBakedProfile)
                0 else profileItem.getSelectableItemBackgroundRes())
            profilesViewModel.stateMonitor.statusLiveData.observe(parentLifeCycle, vpnStateObserver)
        }

        override fun unbind() {
            super.unbind()
            profilesViewModel.stateMonitor.statusLiveData.removeObserver(vpnStateObserver)
        }
    }
}
