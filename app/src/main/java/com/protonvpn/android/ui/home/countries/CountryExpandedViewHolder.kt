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

import android.content.res.ColorStateList
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

open class CountryExpandedViewHolder(
    private val viewModel: CountryListViewModel,
    private val server: Server,
    private val parentLifeCycle: LifecycleOwner,
    private val fastest: Boolean
) : BindableItemEx<ItemServerListBinding>() {

    private val vpnStateObserver = Observer<VpnStateMonitor.Status> {
        // TODO: shouldn't this behave the same as when binding the view?
        //  i.e.: make the button invisible when server is offline, display the maintenance icon
        //  and so on?
        val connected = viewModel.vpnStateMonitor.isConnectedTo(server)
        val colorAttr = when {
            connected -> R.attr.colorAccent
            !server.online -> R.attr.proton_interaction_weak_disabled
            else -> R.attr.proton_interaction_weak
        }
        binding.buttonConnect.backgroundTintList =
            ColorStateList.valueOf(binding.root.getThemeColor(colorAttr))
    }

    override fun getId() = server.serverId.hashCode().toLong()

    override fun bind(viewBinding: ItemServerListBinding, position: Int) {
        super.bind(viewBinding, position)

        // Sometimes we can get 2 binds in a row without unbind in between
        clear()
        val secureCoreEnabled = viewModel.userData.isSecureCoreEnabled
        with(binding) {
            val haveAccess = viewModel.userData.hasAccessToServer(server)

            val textColorAttr = if (haveAccess && server.online)
                R.attr.proton_text_norm
            else
                R.attr.proton_text_hint

            textServer.isVisible = true
            textServer.setTextColor(textServer.getThemeColor(textColorAttr))

            val cityColorAttr = if (haveAccess && server.online)
                R.attr.proton_text_weak
            else
                R.attr.proton_text_hint

            textCity.isVisible = server.city != null && !secureCoreEnabled
            textCity.text = if (server.isFreeServer) "" else server.city
            textCity.setTextColor(textCity.getThemeColor(cityColorAttr))

            buttonUpgrade.isVisible = !haveAccess
            imageWrench.isVisible = haveAccess && !server.online
            buttonConnect.isVisible = haveAccess && server.online

            textLoad.visibility = when {
                haveAccess && server.online -> View.VISIBLE
                haveAccess -> View.INVISIBLE
                else -> View.GONE
            }
            textLoad.text =
                textLoad.resources.getString(R.string.serverLoad, server.load.toInt().toString())
            serverLoadColor.visibility = when {
                !haveAccess -> View.GONE
                !server.online -> View.INVISIBLE
                else -> View.VISIBLE
            }
            serverLoadColor.setColorTint(ServerLoadColor.getColorId(server.loadState))

            imageCountry.isVisible = secureCoreEnabled
            if (secureCoreEnabled) {
                textServer.text = textServer.context.getString(R.string.secureCoreConnectVia,
                    CountryTools.getFullName(server.entryCountry))
                textServer.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_double_right, 0)
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
                EventBus.post(ConnectToServer(connectTo))
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

    override fun getLayout() = R.layout.item_server_list
}
