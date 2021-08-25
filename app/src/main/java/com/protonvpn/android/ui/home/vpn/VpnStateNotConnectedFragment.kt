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

package com.protonvpn.android.ui.home.vpn

import android.animation.LayoutTransition
import android.os.Bundle
import android.view.View
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import com.protonvpn.android.R
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.components.BaseFragmentV2
import com.protonvpn.android.components.ContentLayout
import com.protonvpn.android.databinding.FragmentVpnStateNotConnectedBinding
import com.protonvpn.android.models.config.NetShieldProtocol
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnStateMonitor
import javax.inject.Inject

@ContentLayout(R.layout.fragment_vpn_state_not_connected)
class VpnStateNotConnectedFragment :
    BaseFragmentV2<VpnStateNotConnectedViewModel, FragmentVpnStateNotConnectedBinding>() {

    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

    private lateinit var parentViewModel: VpnStateViewModel

    // All these dependencies are required by the NetShieldSwitch.
    // Once we refactor it, they should be removed.
    @Inject lateinit var userData: UserData
    @Inject lateinit var appConfig: AppConfig
    @Inject lateinit var stateMonitor: VpnStateMonitor
    @Inject lateinit var vpnConnectionManager: VpnConnectionManager
    // End of NetShieldSwitch's dependencies.

    override fun initViewModel() {
        viewModel =
            ViewModelProvider(this, viewModelFactory).get(VpnStateNotConnectedViewModel::class.java)
        parentViewModel =
            ViewModelProvider(requireParentFragment(), viewModelFactory).get(VpnStateViewModel::class.java)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            layoutNotConnected.layoutTransition.enableTransitionType(LayoutTransition.CHANGING)

            // TODO: NetShield onboarding popup.
            netShieldSwitch.init(
                userData.netShieldProtocol,
                appConfig,
                viewLifecycleOwner,
                userData,
                stateMonitor,
                vpnConnectionManager
            ) { s: NetShieldProtocol? ->
                userData.netShieldProtocol = s
            }
            netShieldSwitch.onRadiosExpandClicked = { parentViewModel.onNetShieldExpandClicked() }
            parentViewModel.netShieldExpandStatus.asLiveData()
                .observe(viewLifecycleOwner, Observer { netShieldSwitch.radiosExpanded = it })
            userData.netShieldLiveData.observe(viewLifecycleOwner, Observer<NetShieldProtocol> { state ->
                if (state != null) {
                    netShieldSwitch.setNetShieldValue(state)
                }
            })

            buttonQuickConnect.setOnClickListener {
                viewModel.quickConnect()
            }
        }

        viewModel.ipAddress.observe(viewLifecycleOwner, Observer { ip ->
            binding.textCurrentIp.text = resources.getString(
                R.string.notConnectedCurrentIp,
                if (ip.isEmpty()) getString(R.string.stateFragmentUnknownIp) else ip
            )
        })
    }
}
