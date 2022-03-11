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
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.Theme
import com.protonvpn.android.R
import com.protonvpn.android.databinding.TvStatusViewBinding
import com.protonvpn.android.tv.main.TvMainViewModel
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.utils.HtmlTools
import com.protonvpn.android.utils.getThemeColorId
import com.protonvpn.android.vpn.ErrorType
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import dagger.hilt.android.AndroidEntryPoint
import me.proton.core.util.kotlin.exhaustive
import javax.inject.Inject

@AndroidEntryPoint
class TvStatusFragment : Fragment() {
    private lateinit var binding: TvStatusViewBinding
    @Inject lateinit var vpnStateMonitor: VpnStateMonitor
    @Inject lateinit var serverListUpdater: ServerListUpdater

    private lateinit var viewModel: TvMainViewModel
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = TvStatusViewBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(requireActivity()).get(TvMainViewModel::class.java)
        viewModel.vpnStatus.observe(viewLifecycleOwner, Observer {
            updateState(it)
        })
        return binding.root
    }

    private fun updateState(status: VpnStateMonitor.Status) = with(binding) {
        val state = status.state
        val statusColor = when (state) {
            VpnState.Connected -> R.color.tvAccentLighten
            is VpnState.Error -> R.color.tvAlert
            else -> R.color.white
        }
        textStatus.setTextColor(ContextCompat.getColor(requireContext(), statusColor))

        serverListUpdater.ipAddress.asLiveData().observe(viewLifecycleOwner, Observer {
            val ipToDisplay = when {
                status.state == VpnState.Connected -> status.connectionParams?.exitIpAddress
                it.isEmpty() -> getString(R.string.stateFragmentUnknownIp)
                else -> it
            }
            textIp.text = getString(R.string.ipWithPlaceholder, ipToDisplay)
        })
        when (state) {
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
            VpnState.ScanningPorts, VpnState.CheckingAvailability -> {
                textStatus.text = getString(R.string.loaderCheckingAvailability)
            }
            VpnState.WaitingForNetwork -> {
                textStatus.text = getString(R.string.loaderReconnectNoNetwork)
            }
            VpnState.Disconnecting -> {
                textStatus.text = getString(R.string.loaderDisconnecting)
            }
            is VpnState.Error -> {
                onError(state.type)
            }
        }.exhaustive
    }

    private fun onError(error: ErrorType) = with(binding) {
        when (error) {
            ErrorType.UNREACHABLE ->
                textStatus.setText(R.string.error_server_unreachable)
            // dialog
            ErrorType.AUTH_FAILED ->
                showErrorDialog(R.string.error_auth_failed)
            ErrorType.PEER_AUTH_FAILED ->
                showErrorDialog(R.string.error_peer_auth_failed)
            ErrorType.MAX_SESSIONS ->
                showErrorDialog(R.string.errorMaxSessions)
            ErrorType.POLICY_VIOLATION_DELINQUENT ->
                showErrorDialog(HtmlTools.fromHtml(getString(R.string.errorUserDelinquent)))
            ErrorType.MULTI_USER_PERMISSION ->
                showErrorDialog(R.string.errorTunMultiUserPermission)
            else -> {}
        }
    }

    private fun showErrorDialog(@StringRes stringRes: Int) {
        showErrorDialog(getString(stringRes))
    }

    private fun showErrorDialog(content: CharSequence) {
        viewModel.disconnect("status (TV)")
        MaterialDialog.Builder(requireContext()).theme(Theme.DARK)
            .title(R.string.tv_vpn_error_dialog_title)
            .content(content)
            .cancelable(false)
            .negativeText(R.string.close)
            .show()
    }
}
