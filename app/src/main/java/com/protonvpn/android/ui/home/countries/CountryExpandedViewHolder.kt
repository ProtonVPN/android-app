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
import android.view.View.VISIBLE
import androidx.core.content.ContextCompat
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
import com.protonvpn.android.vpn.VpnStateMonitor

open class CountryExpandedViewHolder(
    private val viewModel: CountryListViewModel,
    private val server: Server,
    private val parentLifeCycle: LifecycleOwner,
    private val fastest: Boolean
) : BindableItemEx<ItemServerListBinding>() {

    private val vpnStateObserver = Observer<VpnStateMonitor.Status> {
        val connected = viewModel.vpnStateMonitor.isConnectedTo(server)
        val colorValue = when {
            connected -> binding.root.getThemeColor(R.attr.colorAccent)
            !server.online -> ContextCompat.getColor(binding.root.context, R.color.interaction_weak_disabled_vpn)
            else -> ContextCompat.getColor(binding.root.context, R.color.interaction_weak_vpn)
        }
        binding.buttonConnect.backgroundTintList = ColorStateList.valueOf(colorValue)
    }

    override fun getId() = server.serverId.hashCode().toLong()

    override fun bind(viewBinding: ItemServerListBinding, position: Int) {
        super.bind(viewBinding, position)

        // Sometimes we can get 2 binds in a row without unbind in between
        clear()
        val context = viewBinding.root.context
        with(binding) {
            val haveAccess = viewModel.userData.hasAccessToServer(server)

            val textColorRes = when {
                !haveAccess -> R.color.text_hint
                !server.online -> R.color.text_disabled
                else -> R.color.text_norm
            }
            textServer.visibility = VISIBLE
            textServer.setTextColor(ContextCompat.getColor(context, textColorRes))

            val cityColorRes = when {
                !haveAccess -> R.color.text_hint
                !server.online -> R.color.text_disabled
                else -> R.color.text_hint
            }
            textCity.isVisible = server.city != null
            textCity.text = if (server.isFreeServer) "" else server.city
            textCity.setTextColor(ContextCompat.getColor(context, cityColorRes))

            buttonUpgrade.isVisible = !haveAccess
            imageWrench.isVisible = haveAccess && !server.online
            buttonConnect.isVisible = haveAccess && server.online

            textLoad.isVisible = haveAccess && server.online
            textLoad.text = "${server.load.toInt()}%"
            serverLoadColor.isVisible = haveAccess && server.online
            serverLoadColor.setColorTint(ServerLoadColor.getColorId(server.loadState))

            imageCountry.isVisible = viewModel.userData.isSecureCoreEnabled
            if (viewModel.userData.isSecureCoreEnabled) {
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
            initFeatureIcons()
            viewModel.vpnStatus.observe(parentLifeCycle, vpnStateObserver)

            val connectUpgradeClickListener = View.OnClickListener {
                val connectTo = if (viewModel.vpnStateMonitor.isConnectedTo(server)) null else server
                EventBus.post(ConnectToServer(connectTo))
            }
            buttonConnect.setOnClickListener(connectUpgradeClickListener)
            buttonUpgrade.setOnClickListener(connectUpgradeClickListener)
        }
    }

    override fun clear() {
        viewModel.vpnStatus.removeObserver(vpnStateObserver)
    }

    private fun initFeatureIcons() = with(binding) {
        val color = ContextCompat.getColor(
            root.context, if (server.online) R.color.icon_hint else R.color.icon_disabled)
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
