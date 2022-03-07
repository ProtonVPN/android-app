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
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.protonvpn.android.R
import com.protonvpn.android.bus.ConnectToServer
import com.protonvpn.android.bus.EventBus
import com.protonvpn.android.databinding.ItemServerListBinding
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.ui.ServerLoadColor
import com.protonvpn.android.utils.BindableItemEx
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.getThemeColor
import com.protonvpn.android.utils.setColorTint
import com.protonvpn.android.utils.setMinSizeTouchDelegate
import com.protonvpn.android.vpn.VpnStateMonitor
import kotlin.math.ceil

class CountryExpandedViewHolder(
    private val viewModel: CountryListViewModel,
    private val server: Server,
    private val parentLifeCycle: LifecycleOwner,
    private val fastest: Boolean
) : BindableItemEx<ItemServerListBinding>() {

    private val vpnStateObserver = Observer<VpnStateMonitor.Status> {
        updateButtons()
    }

    override fun getId() = server.serverId.hashCode().toLong()

    override fun bind(viewBinding: ItemServerListBinding, position: Int) {
        super.bind(viewBinding, position)

        // Sometimes we can get 2 binds in a row without unbind in between
        clear()
        val secureCoreEnabled = viewModel.userData.secureCoreEnabled
        with(binding) {
            val haveAccess = viewModel.hasAccessToServer(server)

            textServer.isVisible = true
            textServer.isEnabled = haveAccess && server.online

            textCity.isVisible = server.city != null && !secureCoreEnabled
            textCity.text = if (server.isFreeServer) "" else server.city
            textCity.isEnabled = haveAccess && server.online

            updateButtons()

            textLoad.visibility = when {
                haveAccess && server.online -> View.VISIBLE
                haveAccess -> View.INVISIBLE
                else -> View.GONE
            }
            textLoad.text =
                textLoad.resources.getString(R.string.serverLoad, server.load.toInt().toString())
            textLoad.minWidth = ceil(
                textLoad.paint.measureText(textLoad.resources.getString(R.string.serverLoad, "100"))
            ).toInt()

            serverLoadColor.visibility = when {
                !haveAccess -> View.GONE
                !server.online -> View.INVISIBLE
                else -> View.VISIBLE
            }
            serverLoadColor.setColorTint(ServerLoadColor.getColorId(server.load))

            imageCountry.isVisible = secureCoreEnabled
            if (secureCoreEnabled) {
                textServer.text = textServer.context.getString(R.string.secureCoreConnectVia,
                    CountryTools.getFullName(server.entryCountry))
                textServer.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    0,
                    0,
                    R.drawable.ic_secure_core_arrow_color,
                    0
                )
                imageCountry.setImageResource(
                    CountryTools.getFlagResource(imageCountry.context, server.entryCountry))
            } else {
                textServer.text = server.serverName
                textServer.setCompoundDrawablesRelative(null, null, null, null)
            }
            buttonConnect.contentDescription = if (fastest) "fastest" else textServer.text
            initFeatureIcons(haveAccess && server.online)
            viewModel.vpnStatus.observe(parentLifeCycle, vpnStateObserver)

            val connectUpgradeClickListener = View.OnClickListener {
                val connectTo =
                    if (viewModel.vpnStateMonitor.isConnectedTo(server)) null else server
                EventBus.post(ConnectToServer("server list power button", connectTo))
            }
            buttonConnect.setOnClickListener(connectUpgradeClickListener)
            buttonConnect.setMinSizeTouchDelegate()
            buttonUpgrade.setOnClickListener(connectUpgradeClickListener)
        }
    }

    override fun clear() {
        viewModel.vpnStatus.removeObserver(vpnStateObserver)
    }

    private fun initFeatureIcons(isServerAvailable: Boolean) = with(binding) {
        val color = root.getThemeColor(
            if (isServerAvailable) R.attr.proton_icon_hint else R.attr.proton_icon_disabled
        )
        featureIcons.isVisible = server.keywords.isNotEmpty()
        if (featureIcons.isVisible) {
            featureIcons.children.forEach { it.isVisible = false }
            server.keywords.forEach {
                val iconView = when (it) {
                    Server.Keyword.P2P -> iconP2P
                    Server.Keyword.TOR -> iconTor
                    Server.Keyword.STREAMING -> iconStreaming
                    Server.Keyword.SMART_ROUTING -> iconSmartRouting
                }
                iconView.isVisible = true
                iconView.setColorFilter(color)
            }
        }
    }

    private fun updateButtons() {
        val connected = viewModel.vpnStateMonitor.isConnectedTo(server)
        val haveAccess = viewModel.hasAccessToServer(server)
        with(binding) {
            buttonUpgrade.isVisible = !haveAccess
            imageWrench.isVisible = haveAccess && !server.online
            buttonConnect.isVisible = haveAccess && server.online
            buttonConnect.isOn = connected
        }
    }

    override fun getLayout() = R.layout.item_server_list

    override fun initializeViewBinding(view: View) = ItemServerListBinding.bind(view)
}
