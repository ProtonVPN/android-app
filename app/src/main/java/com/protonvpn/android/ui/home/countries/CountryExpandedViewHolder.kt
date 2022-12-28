/*
 * Copyright (c) 2019 Proton Technologies AG
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
package com.protonvpn.android.ui.home.countries

import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.protonvpn.android.R
import com.protonvpn.android.bus.ConnectToServer
import com.protonvpn.android.bus.EventBus
import com.protonvpn.android.components.featureIcons
import com.protonvpn.android.databinding.ItemServerListBinding
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.utils.BindableItemEx
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.DisconnectTrigger
import com.protonvpn.android.vpn.VpnStateMonitor

class CountryExpandedViewHolder(
    private val viewModel: CountryListViewModel,
    private val server: Server,
    private val parentLifeCycle: LifecycleOwner,
    private val fastest: Boolean
) : BindableItemEx<ItemServerListBinding>() {

    private val vpnStateObserver = Observer<VpnStateMonitor.Status> {
        with(binding.featuresAndButtons) {
            userHasAccess = viewModel.hasAccessToServer(server)
            isConnected = viewModel.isConnectedToServer(server)
            isOnline = server.online
            viewModel.getServerPartnerships(server)?.let {
                setPartnership(it, server.serverId)
            }
        }
    }

    override fun getId() = server.serverId.hashCode().toLong()

    override fun bind(viewBinding: ItemServerListBinding, position: Int) {
        super.bind(viewBinding, position)

        // Sometimes we can get 2 binds in a row without unbind in between
        clear()
        val secureCoreEnabled = viewModel.isSecureCoreEnabled
        with(binding) {
            root.id = if (fastest) R.id.fastest else View.NO_ID // For tests.
            val haveAccess = viewModel.hasAccessToServer(server)

            textServer.isVisible = true
            textServer.isEnabled = haveAccess && server.online

            textCity.isVisible = server.displayCity != null && !secureCoreEnabled
            textCity.text = if (server.isFreeServer) "" else server.displayCity
            textCity.isEnabled = haveAccess && server.online

            with(featuresAndButtons) {
                featureIcons = server.featureIcons()
                serverLoad = server.load
                isOnline = server.online
                userHasAccess = haveAccess
                isConnected = viewModel.isConnectedToServer(server)
            }

            imageCountry.isVisible = secureCoreEnabled
            if (secureCoreEnabled) {
                textServer.text = textServer.context.getString(
                    R.string.secureCoreConnectVia,
                    CountryTools.getFullName(server.entryCountry)
                )
                textServer.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    0,
                    0,
                    R.drawable.ic_proton_chevrons_right_16,
                    0
                )
                imageCountry.setImageResource(
                    CountryTools.getFlagResource(imageCountry.context, server.entryCountry)
                )
            } else {
                textServer.text = server.serverName
                textServer.setCompoundDrawablesRelative(null, null, null, null)
            }

            viewModel.vpnStatus.observe(parentLifeCycle, vpnStateObserver)

            val connectUpgradeClickListener = View.OnClickListener {
                val event = ConnectToServer(
                    server.takeUnless { viewModel.isConnectedToServer(server) },
                    ConnectTrigger.Server("server list power button"),
                    DisconnectTrigger.Server("server list power button")
                )
                EventBus.post(event)
            }

            featuresAndButtons.setPowerButtonListener(connectUpgradeClickListener)
            // Note: content description is set for UI tests. Figure out a better way of matching the button in tests
            // and use a user-friendly content description, e.g. "Connect"/"Disconnect".
            featuresAndButtons.setPowerButtonContentDescription(if (fastest) "fastest" else textServer.text)
            featuresAndButtons.setUpgradeButtonListener(connectUpgradeClickListener)
        }
    }

    override fun clear() {
        viewModel.vpnStatus.removeObserver(vpnStateObserver)
    }

    override fun getLayout() = R.layout.item_server_list

    override fun initializeViewBinding(view: View) = ItemServerListBinding.bind(view)
}
