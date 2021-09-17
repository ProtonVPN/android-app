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
import com.protonvpn.android.R
import com.protonvpn.android.components.ContentLayout
import com.protonvpn.android.components.NetShieldSwitch
import com.protonvpn.android.databinding.FragmentVpnStateNotConnectedBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
@ContentLayout(R.layout.fragment_vpn_state_not_connected)
class VpnStateNotConnectedFragment :
    VpnStateFragmentWithNetShield<VpnStateNotConnectedViewModel, FragmentVpnStateNotConnectedBinding>() {

    override fun initViewModel() {
        super.initViewModel()
        viewModel =
            ViewModelProvider(this).get(VpnStateNotConnectedViewModel::class.java)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            layoutNotConnected.layoutTransition.enableTransitionType(LayoutTransition.CHANGING)

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

    override fun netShieldSwitch(): NetShieldSwitch = binding.netShieldSwitch
}
