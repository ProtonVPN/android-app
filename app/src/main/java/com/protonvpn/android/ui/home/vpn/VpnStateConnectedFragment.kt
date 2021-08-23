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
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import com.protonvpn.android.R
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.bus.TrafficUpdate
import com.protonvpn.android.components.BaseFragmentV2
import com.protonvpn.android.components.ContentLayout
import com.protonvpn.android.databinding.FragmentVpnStateConnectedBinding
import com.protonvpn.android.models.config.NetShieldProtocol
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.ui.ServerLoadColor.getColor
import com.protonvpn.android.utils.ConnectionTools
import com.protonvpn.android.utils.TimeUtils.getFormattedTimeFromSeconds
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnStateMonitor
import javax.inject.Inject

@ContentLayout(R.layout.fragment_vpn_state_connected)
class VpnStateConnectedFragment :
    BaseFragmentV2<VpnStateConnectedViewModel, FragmentVpnStateConnectedBinding>() {

    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

    // All these dependencies are required by the NetShieldSwitch.
    // Once we refactor it, they should be removed.
    @Inject lateinit var userData: UserData
    @Inject lateinit var appConfig: AppConfig
    @Inject lateinit var stateMonitor: VpnStateMonitor
    @Inject lateinit var vpnConnectionManager: VpnConnectionManager
    // End of NetShieldSwitch's dependencies.

    private lateinit var parentViewModel: VpnStateViewModel

    override fun initViewModel() {
        viewModel =
            ViewModelProvider(this, viewModelFactory).get(VpnStateConnectedViewModel::class.java)
        parentViewModel =
            ViewModelProvider(requireParentFragment(), viewModelFactory).get(VpnStateViewModel::class.java)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            layoutConnected.layoutTransition.enableTransitionType(LayoutTransition.CHANGING)

            buttonSaveToProfile.setOnClickListener { viewModel.saveToProfile() }
            buttonDisconnect.setOnClickListener { parentViewModel.disconnectAndClose() }

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
            userData.netShieldLiveData.observe(viewLifecycleOwner, Observer<NetShieldProtocol> { state ->
                if (state != null) {
                    netShieldSwitch.setNetShieldValue(state)
                }
            })
        }

        viewModel.connectionState.asLiveData().observe(viewLifecycleOwner, Observer {
            updateConnectionState(it)
        })
        viewModel.trafficStatus.observe(viewLifecycleOwner, Observer {
            updateTrafficInfo(it)
        })
        viewModel.eventNotification.asLiveData().observe(viewLifecycleOwner, Observer { textRes ->
            Toast.makeText(requireActivity(), textRes, Toast.LENGTH_LONG).show()
        })

        updateTrafficInfo(TrafficUpdate(0L, 0L, 0L, 0L, 0))
    }

    private fun updateConnectionState(state: VpnStateConnectedViewModel.ConnectionState) {
        with(binding) {
            textServerName.text = state.serverName
            textProtocol.text = state.protocol
            textServerIp.text = state.exitIp
            textLoad.text = getString(R.string.serverLoad, state.serverLoad.toString())
            ImageViewCompat.setImageTintList(
                imageLoad,
                ColorStateList.valueOf(getColor(imageLoad, state.serverLoadState))
            )
        }
    }

    private fun updateTrafficInfo(update: TrafficUpdate?) = with(binding) {
        if (update != null) {
            textSessionTime.text = getFormattedTimeFromSeconds(update.sessionTimeSeconds)
            textUploadSpeed.text = update.uploadSpeedString
            textDownloadSpeed.text = update.downloadSpeedString
            textUploadVolume.text = ConnectionTools.bytesToSize(update.sessionUpload)
            textDownloadVolume.text = ConnectionTools.bytesToSize(update.sessionDownload)
        }
    }
}
