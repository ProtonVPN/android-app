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

import android.graphics.drawable.ColorDrawable
import android.text.InputFilter
import android.text.TextUtils
import android.view.View
import android.view.View.VISIBLE
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.R
import com.protonvpn.android.bus.ConnectToServer
import com.protonvpn.android.bus.EventBus
import com.protonvpn.android.databinding.ItemCountryExpandedBinding
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.utils.BindableItemEx
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.vpn.VpnStateMonitor

open class CountryExpandedViewHolder(
    private val viewModel: CountryListViewModel,
    val server: Server,
    val parentLifeCycle: LifecycleOwner,
    val isShortcut: Boolean
) : BindableItemEx<ItemCountryExpandedBinding>() {

    private val serverSelectionObserver = Observer<Server> {
        markAsSelected(viewModel.selectedServer.value == server, animate = true)
    }

    private val vpnStateObserver = Observer<VpnStateMonitor.Status> {
        val connected = !isShortcut && viewModel.vpnStateMonitor.isConnectedTo(server)
        binding.textConnected.isVisible = connected
        binding.radioServer.isChecked = connected
        binding.radioServer.isEnabled = !connected
    }

    override fun getId() = server.serverId.hashCode().toLong()

    override fun bind(viewBinding: ItemCountryExpandedBinding, position: Int) {
        super.bind(viewBinding, position)

        // Sometimes we can get 2 binds in a row without unbind in between
        clear()
        val context = viewBinding.root.context
        with(binding) {
            val haveAccess = viewModel.userData.hasAccessToServer(server)
            textServer.text = server.displayName
            textServer.visibility = VISIBLE
            textServer.setTextColor(
                    ContextCompat.getColor(context, if (haveAccess) R.color.white else R.color.white50))

            radioServer.isChecked = false
            radioServer.isClickable = false

            textLoad.text = "${server.load.toInt()}%"
            if (server.isOnline) {
                imageLoad.setImageDrawable(
                        ColorDrawable(ContextCompat.getColor(imageLoad.context, server.loadColor)))
            }
            imageLoad.isVisible = server.isOnline
            imageWrench.isVisible = !server.isOnline

            if (viewModel.userData.isSecureCoreEnabled) {
                initSecureCoreServer()
            } else {
                initCasualServer()
            }
            initBadge()
            initConnectedStatus()

            buttonConnect.setOnClickListener {
                val connectTo =
                        if (server == viewModel.vpnStateMonitor.connectingToServer) null else server
                EventBus.post(ConnectToServer(connectTo))
                viewModel.selectedServer.value = null
            }
            buttonConnect.setText(if (haveAccess) R.string.connect else R.string.upgrade)
            initSelection()
        }
    }

    override fun clear() {
        viewModel.selectedServer.removeObserver(serverSelectionObserver)
        viewModel.vpnStateMonitor.vpnStatus.removeObserver(vpnStateObserver)
    }

    private fun initSelection() {
        binding.root.setOnClickListener {
            viewModel.selectedServer.value =
                    if (viewModel.selectedServer.value == server) null else server
        }

        markAsSelected(viewModel.selectedServer.value == server, animate = false)
        viewModel.selectedServer.observe(parentLifeCycle, serverSelectionObserver)
    }

    private fun markAsSelected(enable: Boolean, animate: Boolean) {
        with(binding) {
            radioServer.isChecked = textConnected.visibility == VISIBLE || enable
            showConnectButton(enable, animate)
        }
    }

    private fun showConnectButton(show: Boolean, animate: Boolean) {
        val showConnect = viewModel.vpnStateMonitor.isConnectedTo(server)
        with(binding.buttonConnect) {
            setColor(if (showConnect) R.color.red else R.color.colorAccent)
            setText(when {
                showConnect -> R.string.disconnect
                viewModel.userData.hasAccessToServer(server) -> R.string.connect
                else -> R.string.upgrade
            })
        }
        binding.buttonConnect.setExpanded(show, animate, parentLifeCycle.lifecycleScope)
    }

    private fun initCasualServer() {
        with(binding) {
            imageCountry.visibility = View.GONE
            textServer.visibility = VISIBLE
            textServer.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(9))
            textServer.text = server.serverName
            textIp.visibility = VISIBLE
            textIp.setTextColor(ContextCompat.getColor(textIp.context, R.color.lightGrey))
            textIp.text = if (server.isOnline)
                if (viewModel.userData.hasAccessToServer(server))
                    if (server.isFreeServer) "" else server.city
                else
                    binding.textIp.context.getString(R.string.premium)
            else
                binding.textIp.context.getString(R.string.listItemMaintenance)
            textLoad.isVisible = server.isOnline
            imageDoubleArrows.visibility = View.GONE
        }
    }

    private fun initSecureCoreServer() {
        with(binding) {
            imageCountry.visibility = VISIBLE
            textServer.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(20))
            textServer.visibility = View.GONE
            textIp.setTextColor(ContextCompat.getColor(root.context, R.color.white))
            textIp.text = binding.root.context.getString(R.string.secureCoreConnectVia,
                    CountryTools.getFullName(server.entryCountry))
            imageCountry.setImageResource(
                    CountryTools.getFlagResource(imageCountry.context, server.entryCountry!!))
            imageDoubleArrows.visibility = VISIBLE
            textServer.ellipsize = TextUtils.TruncateAt.END
        }
    }

    private fun initBadge() {
        with(binding) {
            serverBadge.visibility =
                    if (server.keywords.isEmpty()) View.GONE else VISIBLE
            if (server.keywords.contains("p2p")) {
                serverBadge.primaryText = "P2P"
            }
            if (server.keywords.contains("tor")) {
                serverBadge.primaryText = "TOR"
            }
        }
    }

    private fun initConnectedStatus() {
        viewModel.vpnStateMonitor.vpnStatus.observe(parentLifeCycle, vpnStateObserver)
    }

    override fun getLayout() = R.layout.item_country_expanded
}
