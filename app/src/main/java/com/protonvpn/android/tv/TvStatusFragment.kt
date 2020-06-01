/*
 * Copyright (c) 2020 Proton Technologies AG
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
package com.protonvpn.android.tv

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.protonvpn.android.R
import com.protonvpn.android.databinding.TvStatusViewBinding
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import dagger.android.support.DaggerFragment
import javax.inject.Inject

class TvStatusFragment : DaggerFragment() {
    private lateinit var binding: TvStatusViewBinding
    @Inject lateinit var vpnStateMonitor: VpnStateMonitor
    @Inject lateinit var serverListUpdater: ServerListUpdater

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = TvStatusViewBinding.inflate(inflater, container, false)

        vpnStateMonitor.vpnStatus.observe(
            viewLifecycleOwner,
            Observer {
                updateState(it)
            }
        )
        return binding.root
    }

    private fun updateState(status: VpnStateMonitor.Status) = with(binding) {
        val statusColor = if (status.state == VpnState.Connected) R.color.colorAccent else R.color.white
        binding.textStatus.setTextColor(ContextCompat.getColor(requireContext(), statusColor))

        val ipToDisplay =
            if (status.state == VpnState.Connected) status.connectionParams?.exitIpAddress
            else serverListUpdater.ipAddress.value ?: R.string.stateFragmentUnknownIp
        binding.textIp.text = getString(R.string.ipWithPlaceholder, ipToDisplay)

        when (status.state) {
            VpnState.Connected -> {
                textStatus.text = getString(R.string.stateConnectedTo, status.server?.displayName)
            }
            VpnState.Connecting -> {
                textStatus.text = getString(R.string.state_connecting)
            }
            VpnState.Disabled -> {
                textStatus.text = getString(R.string.stateNotConnected)
            }
            VpnState.Reconnecting -> {
                textStatus.text = getString(R.string.state_reconnecting)
            }
        }
    }
}
