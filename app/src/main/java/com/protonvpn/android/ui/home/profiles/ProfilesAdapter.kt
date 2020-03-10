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

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import butterknife.BindView
import butterknife.OnClick
import com.protonvpn.android.R
import com.protonvpn.android.bus.ConnectToProfile
import com.protonvpn.android.bus.EventBus
import com.protonvpn.android.bus.ServerSelected
import com.protonvpn.android.bus.VpnStateChanged
import com.protonvpn.android.components.BaseViewHolder
import com.protonvpn.android.components.TriangledTextView
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.ui.home.profiles.ProfileActivity.Companion.navigateForEdit
import com.protonvpn.android.ui.home.profiles.ProfilesAdapter.ServersViewHolder
import com.protonvpn.android.utils.EventBusBinder
import com.squareup.otto.Subscribe
import kotlinx.coroutines.CoroutineScope

class ProfilesAdapter(
    private val profilesFragment: ProfilesFragment,
    private val profilesViewModel: ProfilesViewModel,
    private val scope: CoroutineScope
) : RecyclerView.Adapter<ServersViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ServersViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_profile, parent, false))

    override fun onBindViewHolder(holder: ServersViewHolder, position: Int) {
        holder.bindData(profilesViewModel.getProfile(position))
    }

    override fun onViewRecycled(holder: ServersViewHolder) {
        holder.unbind()
    }

    override fun getItemCount() = profilesViewModel.profileCount

    inner class ServersViewHolder(view: View) : BaseViewHolder<Profile>(view), View.OnClickListener {

        @BindView(R.id.textServer) lateinit var textServer: TextView
        @BindView(R.id.radioServer) lateinit var radioServer: RadioButton
        @BindView(R.id.buttonConnect) lateinit var buttonConnect: TriangledTextView
        @BindView(R.id.textConnected) lateinit var textConnected: TextView
        @BindView(R.id.textServerNotSet) lateinit var textServerNotSet: TextView
        @BindView(R.id.imageCountry) lateinit var imageCountry: ImageView
        @BindView(R.id.layoutProfileColor) lateinit var layoutProfileColor: View
        @BindView(R.id.imageEdit) lateinit var imageEdit: ImageView

        private var server: Server? = null

        private val eventBusBinder = EventBusBinder(this)

        init {
            view.setOnClickListener(this)
        }

        @OnClick(R.id.buttonConnect)
        fun buttonConnect() {
            if (server != null) {
                EventBus.post(ConnectToProfile(item))
                buttonConnect.setExpanded(expand = false, animate = true, scope = scope)
            }
        }

        override fun bindData(newItem: Profile) {
            super.bindData(newItem)
            server = newItem.server

            textServer.text = newItem.getDisplayName(textServer.context)
            radioServer.isChecked = false
            radioServer.isClickable = false
            buttonConnect.setExpanded(expand = false, animate = false, scope = scope)
            buttonConnect.setText(profilesViewModel.getConnectTextRes(server))
            initConnectedStatus()
            textServerNotSet.visibility = if (server != null) View.GONE else View.VISIBLE
            imageEdit.visibility =
                    if (newItem.isPreBakedProfile) View.INVISIBLE else View.VISIBLE
            layoutProfileColor.setBackgroundColor(Color.parseColor(newItem.color))
            eventBusBinder.register()
        }

        override fun unbind() {
            eventBusBinder.unregister()
            server = null
            super.unbind()
        }

        private fun initConnectedStatus() {
            val connectedToServer = profilesViewModel.isConnectedTo(server)
            textConnected.visibility = if (connectedToServer) View.VISIBLE else View.GONE
            radioServer.isChecked = connectedToServer
        }

        @Subscribe
        fun onServerSelected(selection: ServerSelected) {
            if (radioServer.isChecked && !selection.isSameSelection(item, server)) {
                markAsSelected(false)
                initConnectedStatus()
            }
        }

        private fun markAsSelected(enable: Boolean) {
            buttonConnect.isClickable = enable
            if (textConnected.visibility != View.VISIBLE) {
                radioServer.isChecked = enable
                buttonConnect.setExpanded(enable, true, scope)
            }
        }

        @OnClick(R.id.imageEdit)
        fun imageEdit() {
            navigateForEdit(profilesFragment, item)
        }

        override fun onClick(v: View) {
            if (!profilesViewModel.isConnectedTo(server) && server != null) {
                markAsSelected(!radioServer.isChecked)
                EventBus.post(ServerSelected(item, server))
            }
        }

        @Subscribe
        fun onVpnStateChanged(event: VpnStateChanged) {
            buttonConnect.setExpanded(expand = false, animate = true, scope = scope)
            initConnectedStatus()
            EventBus.post(ServerSelected(item, item.server))
        }
    }
}
