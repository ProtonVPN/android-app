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
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.protonvpn.android.R
import com.protonvpn.android.components.featureIcons
import com.protonvpn.android.databinding.ItemServerListBinding
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.base.ui.Flag
import com.protonvpn.android.redesign.app.ui.MainActivity
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.utils.BindableItemEx
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.getActivity
import com.protonvpn.android.vpn.VpnStateMonitor
import java.util.Objects

class ServerGroupServerViewHolder(
    private val viewModel: CountryListViewModel,
    private val server: Server,
    private val serverGroupModel: CollapsibleServerGroupModel,
    private val parentLifeCycle: LifecycleOwner,
    private val fastest: Boolean,
    private val groupId: String
) : BindableItemEx<ItemServerListBinding>() {

    private val vpnStateObserver = Observer<VpnStateMonitor.Status> {
        with(binding.featuresAndButtons) {
            userHasAccess = serverGroupModel.hasAccessToServer(server)
            isConnected = viewModel.isConnectedToServer(server)
            isOnline = server.online
            setPartnership(viewModel.getServerPartnerships(server), server.serverId)
        }
    }

    override fun getId() = Objects.hash(server.serverId, groupId).toLong()

    override fun bind(viewBinding: ItemServerListBinding, position: Int) {
        super.bind(viewBinding, position)

        // Sometimes we can get 2 binds in a row without unbind in between
        clear()
        val secureCoreEnabled = serverGroupModel.secureCore
        with(binding) {
            root.id = if (fastest) R.id.fastest else View.NO_ID // For tests.
            val haveAccess = serverGroupModel.hasAccessToServer(server)

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

            composeViewFlag.isVisible = secureCoreEnabled
            if (secureCoreEnabled) {
                textServer.text = textServer.context.getString(
                    R.string.secureCoreConnectVia,
                    CountryTools.getFullName(server.entryCountry)
                )
                composeViewFlag.setContent {
                    Flag(
                        exitCountry = CountryId(server.entryCountry),
                        entryCountry = CountryId.fastest,
                        modifier = Modifier.size(24.dp, 16.dp)
                    )
                }
            } else {
                textServer.text = server.serverName
                textServer.setCompoundDrawablesRelative(null, null, null, null)
            }

            viewModel.vpnStatus.observe(parentLifeCycle, vpnStateObserver)
            val connectUpgradeClickListener = View.OnClickListener {
                viewModel.connectOrDisconnect(
                    (root.context.getActivity() as MainActivity).vpnActivityDelegate,
                    determineConnectIntent(),
                    triggerDescription = "server list power button")
            }

            featuresAndButtons.setPowerButtonListener(connectUpgradeClickListener)
            // Note: content description is set for UI tests. Figure out a better way of matching the button in tests
            // and use a user-friendly content description, e.g. "Connect"/"Disconnect".
            featuresAndButtons.setPowerButtonContentDescription(if (fastest) "fastest" else textServer.text)
            featuresAndButtons.setUpgradeButtonListener(connectUpgradeClickListener)
        }
    }
    private fun determineConnectIntent(): ConnectIntent {
        return when {
            server.isSecureCoreServer -> ConnectIntent.SecureCore(
                CountryId(server.exitCountry),
                CountryId(server.entryCountry)
            )
            fastest && server.isGatewayServer -> ConnectIntent.Gateway(server.gatewayName!!, serverId = null)
            fastest -> ConnectIntent.FastestInCountry(CountryId(server.exitCountry), emptySet())
            server.isGatewayServer -> ConnectIntent.Gateway(server.gatewayName!!, server.serverId)
            else -> ConnectIntent.Server(server.serverId, emptySet())
        }
    }

    override fun clear() {
        viewModel.vpnStatus.removeObserver(vpnStateObserver)
    }

    override fun getLayout() = R.layout.item_server_list

    override fun initializeViewBinding(view: View) = ItemServerListBinding.bind(view)
}
